package dev.paydeck.api.access;

import java.util.Locale;
import java.util.UUID;

import dev.paydeck.api.account.Account;
import dev.paydeck.api.account.AccountKind;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.audit.AuditLog;
import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.Role;
import dev.paydeck.api.card.Card;
import dev.paydeck.api.card.CardRepository;
import dev.paydeck.api.common.ForbiddenException;
import dev.paydeck.api.common.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Every "may this person do this to this thing?" decision in one place.
 *
 * <p>The rules, as a table:
 * <pre>
 *                     owner  cardholder  support  admin
 * account: view         yes      -         yes     yes
 * account: move money   yes      -         -       -
 * account: issue card   yes      -         -       -
 * card: view            yes     yes        yes     yes
 * card: freeze          yes     yes        yes     yes
 * card: unfreeze        yes      -         yes     yes
 * card: change settings yes      -         -       -
 * card: cancel          yes      -         -       yes
 * </pre>
 * Staff never move customers' money. Someone who cannot even see a thing
 * gets "not found", so they cannot learn that it exists; someone who can see
 * it but not change it gets "not allowed".
 */
@Component
public class AccessPolicy {

    private final AccountRepository accounts;
    private final CardRepository cards;
    private final AuditLog audit;

    public AccessPolicy(AccountRepository accounts, CardRepository cards, AuditLog audit) {
        this.accounts = accounts;
        this.cards = cards;
        this.audit = audit;
    }

    static boolean allows(Role role, Relationship relationship, AccountAction action) {
        boolean staff = role == Role.SUPPORT || role == Role.ADMIN;
        boolean owner = relationship == Relationship.OWNER;
        return switch (action) {
            case VIEW -> owner || staff;
            case MOVE_MONEY, ISSUE_CARD -> owner;
        };
    }

    static boolean allows(Role role, Relationship relationship, CardAction action) {
        boolean staff = role == Role.SUPPORT || role == Role.ADMIN;
        boolean owner = relationship == Relationship.OWNER;
        return switch (action) {
            case VIEW, FREEZE -> relationship != Relationship.NONE || staff;
            case UNFREEZE -> owner || staff;
            case CHANGE_SETTINGS -> owner;
            case CANCEL -> owner || role == Role.ADMIN;
        };
    }

    /** Loads a customer account if this person may do {@code action} with it. */
    public Account account(CurrentUser user, UUID accountId, AccountAction action) {
        Account account = accounts.findById(accountId)
                .filter(a -> a.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Account", accountId));
        Relationship relationship = user.id().equals(account.getOwnerUserId())
                ? Relationship.OWNER : Relationship.NONE;
        check(allows(user.role(), relationship, AccountAction.VIEW), allows(user.role(), relationship, action),
                "Account", accountId);
        recordStaffAccess(user, relationship, "ACCOUNT_" + action, "ACCOUNT", accountId);
        return account;
    }

    /** Loads a card if this person may do {@code action} with it. */
    public Card card(CurrentUser user, UUID cardId, CardAction action) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        UUID ownerId = accounts.findById(card.getAccountId()).map(Account::getOwnerUserId).orElse(null);
        Relationship relationship = user.id().equals(ownerId) ? Relationship.OWNER
                : user.id().equals(card.getCardholderUserId()) ? Relationship.CARDHOLDER
                : Relationship.NONE;
        check(allows(user.role(), relationship, CardAction.VIEW), allows(user.role(), relationship, action),
                "Card", cardId);
        recordStaffAccess(user, relationship, "CARD_" + action, "CARD", cardId);
        return card;
    }

    private static void check(boolean canSee, boolean canDo, String thing, UUID id) {
        if (!canSee) {
            throw new NotFoundException(thing, id);
        }
        if (!canDo) {
            throw new ForbiddenException("You are not allowed to do this to this " + thing.toLowerCase(Locale.ROOT)
                    + ".");
        }
    }

    /** Staff looking at customers' things is exactly what an audit log is for. */
    private void recordStaffAccess(CurrentUser user, Relationship relationship, String action, String targetType,
                                   UUID targetId) {
        if (relationship == Relationship.NONE && user.isStaff()) {
            audit.recordNow(user.id(), action, targetType, targetId, "by " + user.role());
        }
    }
}
