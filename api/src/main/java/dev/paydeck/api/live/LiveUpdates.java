package dev.paydeck.api.live;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import dev.paydeck.api.account.Account;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.auth.User;
import dev.paydeck.api.auth.UserRepository;
import dev.paydeck.api.card.CardRepository;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.push.Device;
import dev.paydeck.api.push.DeviceRepository;
import dev.paydeck.api.push.PushSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells people what happened, the moment it is safely saved:
 * <ul>
 *   <li>over WebSockets, to every open app watching the account
 *   ({@code /topic/accounts/{id}}) or the person ({@code /topic/users/{id}});</li>
 *   <li>as a push notification, to the phones of the owner and the
 *   cardholder, if they want that kind of notification.</li>
 * </ul>
 */
@Component
public class LiveUpdates {

    private final SimpMessagingTemplate stomp;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final UserRepository users;
    private final DeviceRepository devices;
    private final LedgerService ledger;
    private final PushSender push;

    public LiveUpdates(SimpMessagingTemplate stomp, AccountRepository accounts, CardRepository cards,
                       UserRepository users, DeviceRepository devices, LedgerService ledger, PushSender push) {
        this.stomp = stomp;
        this.accounts = accounts;
        this.cards = cards;
        this.users = users;
        this.devices = devices;
        this.ledger = ledger;
        this.push = push;
    }

    /**
     * Runs after the transaction that published the event has committed, on
     * another thread, so a slow phone network never slows down a payment.
     */
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(AccountEvent event) {
        Account account = accounts.findById(event.accountId()).orElseThrow();
        LiveMessage message = new LiveMessage(event.accountId(), event.kind(), event.title(), event.body(),
                event.cardId(), ledger.balanceOf(event.accountId()), Instant.now());

        stomp.convertAndSend("/topic/accounts/" + event.accountId(), message);

        Set<UUID> people = new LinkedHashSet<>();
        if (account.getOwnerUserId() != null) {
            people.add(account.getOwnerUserId());
        }
        if (event.cardId() != null) {
            cards.findById(event.cardId())
                    .map(card -> card.getCardholderUserId())
                    .ifPresent(people::add);
        }
        for (UUID personId : people) {
            if (!personId.equals(account.getOwnerUserId())) {
                // Cardholders cannot watch the account, so they get it on their own topic.
                stomp.convertAndSend("/topic/users/" + personId, message);
            }
            users.findById(personId).filter(user -> wants(user, event)).ifPresent(user -> notifyPhones(user, event));
        }
    }

    private static boolean wants(User user, AccountEvent event) {
        if (event.kind() == AccountEvent.Kind.CARD_UPDATED) {
            return false; // Shown in the app, but not worth buzzing a phone for.
        }
        return event.kind().isDecline() ? user.isNotifyDeclines() : user.isNotifyPayments();
    }

    private void notifyPhones(User user, AccountEvent event) {
        for (Device device : devices.findByUserId(user.getId())) {
            if (push.send(device.getToken(), event.title(), event.body()) == PushSender.Result.TOKEN_GONE) {
                devices.deleteByToken(device.getToken());
            }
        }
    }
}
