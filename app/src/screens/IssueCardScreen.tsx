import React, { useState } from 'react';
import { Text } from 'react-native';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { useWallet } from '../state/WalletContext';
import { useTheme } from '../theme';
import { limitError, nicknameError, parseLimit } from '../validation';

export function IssueCardScreen() {
  const theme = useTheme();
  const { goBack } = useNavigation();
  const { issueCard } = useWallet();
  const [nickname, setNickname] = useState('');
  const [limit, setLimit] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const errors = {
    nickname: nicknameError(nickname),
    limit: limitError(limit),
  };

  const issue = () => {
    setSubmitted(true);
    if (!errors.nickname && !errors.limit) {
      issueCard(nickname.trim(), parseLimit(limit));
      goBack();
    }
  };

  return (
    <Screen title="New card" leading={<BackButton />}>
      <Text style={{ color: theme.muted }}>
        A virtual card spends from your wallet. You can freeze it or cancel it
        at any time.
      </Text>
      <Field
        label="Card name"
        value={nickname}
        onChangeText={setNickname}
        placeholder="For example, Groceries"
        maxLength={50}
        error={submitted ? errors.nickname : null}
      />
      <Field
        label="Spending limit per payment (optional)"
        value={limit}
        onChangeText={setLimit}
        keyboardType="decimal-pad"
        placeholder="No limit"
        hint="Payments above this amount will be declined."
        error={submitted ? errors.limit : null}
      />
      <Button title="Issue card" onPress={issue} />
    </Screen>
  );
}
