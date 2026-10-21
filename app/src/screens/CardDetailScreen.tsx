import React, { useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import { PaymentCardView } from '../components/PaymentCardView';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { useWallet } from '../state/WalletContext';
import { spacing, useTheme } from '../theme';
import { limitError, nicknameError, parseLimit } from '../validation';

export function CardDetailScreen({ cardId }: { cardId: string }) {
  const theme = useTheme();
  const { goBack } = useNavigation();
  const wallet = useWallet();
  const card = wallet.state.cards.find(c => c.id === cardId);

  const [nickname, setNickname] = useState(card?.nickname ?? '');
  const [limit, setLimit] = useState(
    card?.spendingLimitCents != null
      ? String(card.spendingLimitCents / 100)
      : '',
  );
  const [saved, setSaved] = useState(false);

  if (!card) {
    return (
      <Screen title="Card not found" leading={<BackButton />}>
        <Text style={{ color: theme.muted }}>This card no longer exists.</Text>
      </Screen>
    );
  }

  const errors = {
    nickname: nicknameError(nickname),
    limit: limitError(limit),
  };

  const save = () => {
    if (errors.nickname || errors.limit) {
      return;
    }
    wallet.renameCard(card.id, nickname.trim());
    wallet.setSpendingLimit(card.id, parseLimit(limit));
    setSaved(true);
  };

  const confirmCancel = () =>
    Alert.alert(
      'Cancel this card?',
      'A cancelled card can never be used again.',
      [
        { text: 'Keep card', style: 'cancel' },
        {
          text: 'Cancel card',
          style: 'destructive',
          onPress: () => {
            wallet.cancelCard(card.id);
            goBack();
          },
        },
      ],
    );

  return (
    <Screen title={card.nickname} leading={<BackButton />}>
      <PaymentCardView card={card} currency={wallet.state.account.currency} />

      {card.status === 'ACTIVE' && (
        <Button
          title="Freeze card"
          variant="secondary"
          onPress={() => wallet.freezeCard(card.id)}
        />
      )}
      {card.status === 'FROZEN' && (
        <Button
          title="Unfreeze card"
          onPress={() => wallet.unfreezeCard(card.id)}
        />
      )}

      <View style={{ gap: spacing.m }}>
        <Field
          label="Card name"
          value={nickname}
          onChangeText={text => {
            setNickname(text);
            setSaved(false);
          }}
          maxLength={50}
          error={errors.nickname}
        />
        <Field
          label="Spending limit per payment"
          value={limit}
          onChangeText={text => {
            setLimit(text);
            setSaved(false);
          }}
          keyboardType="decimal-pad"
          placeholder="No limit"
          error={errors.limit}
        />
        <Button
          title={saved ? 'Saved' : 'Save changes'}
          variant="secondary"
          onPress={save}
        />
      </View>

      <Button title="Cancel card" variant="danger" onPress={confirmCancel} />
    </Screen>
  );
}
