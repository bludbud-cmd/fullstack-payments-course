package dev.paydeck.api.access;

import static dev.paydeck.api.access.Relationship.CARDHOLDER;
import static dev.paydeck.api.access.Relationship.NONE;
import static dev.paydeck.api.access.Relationship.OWNER;
import static dev.paydeck.api.auth.Role.ADMIN;
import static dev.paydeck.api.auth.Role.CUSTOMER;
import static dev.paydeck.api.auth.Role.SUPPORT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The permission table from AccessPolicy's documentation, checked line by line. */
class AccessPolicyTest {

    @Test
    void onlyOwnersMoveMoney() {
        assertThat(AccessPolicy.allows(CUSTOMER, OWNER, AccountAction.MOVE_MONEY)).isTrue();
        assertThat(AccessPolicy.allows(SUPPORT, NONE, AccountAction.MOVE_MONEY)).isFalse();
        assertThat(AccessPolicy.allows(ADMIN, NONE, AccountAction.MOVE_MONEY)).isFalse();
        assertThat(AccessPolicy.allows(CUSTOMER, NONE, AccountAction.MOVE_MONEY)).isFalse();
    }

    @Test
    void staffCanLookAtAnyAccount() {
        assertThat(AccessPolicy.allows(SUPPORT, NONE, AccountAction.VIEW)).isTrue();
        assertThat(AccessPolicy.allows(ADMIN, NONE, AccountAction.VIEW)).isTrue();
        assertThat(AccessPolicy.allows(CUSTOMER, NONE, AccountAction.VIEW)).isFalse();
    }

    @Test
    void cardholdersCanFreezeButNotUnfreezeOrChangeTheirCard() {
        assertThat(AccessPolicy.allows(CUSTOMER, CARDHOLDER, CardAction.VIEW)).isTrue();
        assertThat(AccessPolicy.allows(CUSTOMER, CARDHOLDER, CardAction.FREEZE)).isTrue();
        assertThat(AccessPolicy.allows(CUSTOMER, CARDHOLDER, CardAction.UNFREEZE)).isFalse();
        assertThat(AccessPolicy.allows(CUSTOMER, CARDHOLDER, CardAction.CHANGE_SETTINGS)).isFalse();
        assertThat(AccessPolicy.allows(CUSTOMER, CARDHOLDER, CardAction.CANCEL)).isFalse();
    }

    @Test
    void supportCanFreezeAndUnfreezeButNotCancel() {
        assertThat(AccessPolicy.allows(SUPPORT, NONE, CardAction.FREEZE)).isTrue();
        assertThat(AccessPolicy.allows(SUPPORT, NONE, CardAction.UNFREEZE)).isTrue();
        assertThat(AccessPolicy.allows(SUPPORT, NONE, CardAction.CANCEL)).isFalse();
        assertThat(AccessPolicy.allows(SUPPORT, NONE, CardAction.CHANGE_SETTINGS)).isFalse();
    }

    @Test
    void adminsCanCancelButNotChangeSettings() {
        assertThat(AccessPolicy.allows(ADMIN, NONE, CardAction.CANCEL)).isTrue();
        assertThat(AccessPolicy.allows(ADMIN, NONE, CardAction.CHANGE_SETTINGS)).isFalse();
    }

    @Test
    void strangersCanDoNothingWithACard() {
        for (CardAction action : CardAction.values()) {
            assertThat(AccessPolicy.allows(CUSTOMER, NONE, action)).as(action.name()).isFalse();
        }
    }
}
