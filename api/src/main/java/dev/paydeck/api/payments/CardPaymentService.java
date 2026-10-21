package dev.paydeck.api.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.paydeck.api.account.Account;
import dev.paydeck.api.account.AccountKind;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.card.Card;
import dev.paydeck.api.card.CardRepository;
import dev.paydeck.api.card.CardStatus;
import dev.paydeck.api.common.BusinessRuleException;
import dev.paydeck.api.common.IdempotencyConflictException;
import dev.paydeck.api.common.MoneyFormat;
import dev.paydeck.api.common.NotFoundException;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.ledger.LedgerTransaction;
import dev.paydeck.api.ledger.Posting;
import dev.paydeck.api.ledger.TransactionType;
import dev.paydeck.api.live.AccountEvent;
import dev.paydeck.api.money.MoneyMovementService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a card network asks PayDeck every time a card is used: "may this card
 * pay this much?" The answer is approved (and the money moves) or declined
 * (with a reason, and no money moves).
 */
@Service
public class CardPaymentService {

    private final CardRepository cards;
    private final AccountRepository accounts;
    private final CardPaymentRepository payments;
    private final LedgerService ledger;
    private final ApplicationEventPublisher events;

    public CardPaymentService(CardRepository cards, AccountRepository accounts, CardPaymentRepository payments,
                              LedgerService ledger, ApplicationEventPublisher events) {
        this.cards = cards;
        this.accounts = accounts;
        this.payments = payments;
        this.ledger = ledger;
        this.events = events;
    }

    @Transactional
    public CardPayment pay(UUID cardId, long amountCents, String merchant, String idempotencyKey) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        // Lock the account first: two payments at once must not both spend the same money.
        Account account = accounts.findByIdForUpdate(card.getAccountId()).orElseThrow();
        String fingerprint = MoneyMovementService.fingerprint("CARD_PAYMENT", cardId, amountCents, merchant);

        Optional<CardPayment> earlier = payments.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) {
            if (!earlier.get().getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return earlier.get();
        }

        String amount = MoneyFormat.format(amountCents, account.getCurrency());
        DeclineReason reason = declineReason(card, account, amountCents);
        if (reason != null) {
            CardPayment declined = payments.save(CardPayment.declined(cardId, account.getId(), merchant, amountCents,
                    reason, idempotencyKey, fingerprint));
            events.publishEvent(new AccountEvent(account.getId(), AccountEvent.Kind.CARD_DECLINED,
                    "Payment declined", amount + " at " + merchant + " with " + card.getNickname() + ". "
                            + reason.message(), cardId));
            return declined;
        }

        LedgerTransaction transaction = ledger.record(TransactionType.CARD_PAYMENT, merchant,
                "card-payment:" + idempotencyKey, fingerprint,
                List.of(new Posting(account.getId(), -amountCents), new Posting(outsideWorld(account), amountCents)));
        CardPayment approved = payments.save(CardPayment.approved(cardId, account.getId(), merchant, amountCents,
                transaction.getId(), idempotencyKey, fingerprint));
        events.publishEvent(new AccountEvent(account.getId(), AccountEvent.Kind.CARD_PAYMENT,
                "You paid " + merchant, amount + " with " + card.getNickname(), cardId));
        return approved;
    }

    /** The checks, in the order a person would want to hear about them. */
    static DeclineReason declineReason(Card card, long balanceCents, long amountCents) {
        if (card.getStatus() == CardStatus.FROZEN) {
            return DeclineReason.CARD_FROZEN;
        }
        if (card.getStatus() == CardStatus.CANCELLED) {
            return DeclineReason.CARD_CANCELLED;
        }
        if (card.getSpendingLimitCents() != null && amountCents > card.getSpendingLimitCents()) {
            return DeclineReason.OVER_LIMIT;
        }
        if (balanceCents < amountCents) {
            return DeclineReason.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    private DeclineReason declineReason(Card card, Account account, long amountCents) {
        return declineReason(card, ledger.balanceOf(account.getId()), amountCents);
    }

    /** Gives the money back, for example after a dispute. Staff only; see the controller. */
    @Transactional
    public CardPayment refund(UUID paymentId) {
        CardPayment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Card payment", paymentId));
        if (payment.getStatus() != CardPaymentStatus.APPROVED) {
            throw new BusinessRuleException("NOT_REFUNDABLE", "Only an approved payment can be refunded");
        }
        Account account = accounts.findByIdForUpdate(payment.getAccountId()).orElseThrow();
        LedgerTransaction refund = ledger.record(TransactionType.REFUND, "Refund: " + payment.getMerchant(),
                "refund:" + paymentId, MoneyMovementService.fingerprint("REFUND", paymentId),
                List.of(new Posting(outsideWorld(account), -payment.getAmountCents()),
                        new Posting(account.getId(), payment.getAmountCents())));
        payment.refunded(refund.getId());
        events.publishEvent(new AccountEvent(account.getId(), AccountEvent.Kind.REFUND, "Refund from "
                + payment.getMerchant(), MoneyFormat.format(payment.getAmountCents(), account.getCurrency())
                + " is back in your wallet", payment.getCardId()));
        return payment;
    }

    /** Shops are outside PayDeck, so card money goes to the "outside world" account. */
    private UUID outsideWorld(Account account) {
        return accounts.findByKindAndCurrency(AccountKind.SYSTEM, account.getCurrency()).orElseThrow().getId();
    }
}
