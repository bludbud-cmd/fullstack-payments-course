import React, { useState } from 'react';
import { Text } from 'react-native';
import { useIssueCard } from '../api/hooks';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { useTheme } from '../theme';
import { limitError, nicknameError, parseLimit } from '../validation';

export function IssueCardScreen() {
  const theme = useTheme();
  const { goBack } = useNavigation();
  const issueCard = useIssueCard();
  const [nickname, setNickname] = useState('');
  const [limit, setLimit] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const errors = {
    nickname: nicknameError(nickname),
    limit: limitError(limit),
  };

  const issue = () => {
    setSubmitted(true);
    if (!errors.nickname && !errors.limit && !issueCard.isPending) {
      issueCard.mutate(
        { nickname: nickname.trim(), spendingLimitCents: parseLimit(limit) },
        { onSuccess: goBack },
      );
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
      <ErrorText error={issueCard.error} />
      <Button
        title={issueCard.isPending ? 'Issuing…' : 'Issue card'}
        onPress={issue}
        disabled={issueCard.isPending}
      />
    </Screen>
  );
}
