import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import { spacing, useTheme } from '../theme';
import { emailError, passwordError } from '../validation';

/** Sign in, or create an account. One screen, two modes. */
export function SignInScreen() {
  const theme = useTheme();
  const { signIn, signUp, signInWithGoogle, googleError } = useAuth();
  const [mode, setMode] = useState<'signIn' | 'signUp'>('signIn');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const signingUp = mode === 'signUp';
  const errors = {
    name: signingUp && name.trim() === '' ? 'Tell us your name' : null,
    email: emailError(email),
    password: signingUp
      ? passwordError(password)
      : password === ''
      ? 'Enter your password'
      : null,
  };
  const valid = !errors.name && !errors.email && !errors.password;

  const submit = async () => {
    setSubmitted(true);
    if (!valid || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (signingUp) {
        await signUp(name.trim(), email.trim(), password);
      } else {
        await signIn(email.trim(), password);
      }
    } catch (e) {
      setError(e as Error);
      setBusy(false);
    }
  };

  const switchMode = () => {
    setMode(signingUp ? 'signIn' : 'signUp');
    setSubmitted(false);
    setError(null);
  };

  // The API checks the email too. Show its answer next to the right field.
  const serverFieldError = (field: string) =>
    error instanceof ApiError ? error.fields?.[field] ?? null : null;

  return (
    <View style={[styles.root, { backgroundColor: theme.background }]}>
      <Screen
        title={signingUp ? 'Create your PayDeck account' : 'Sign in to PayDeck'}
      >
        {signingUp && (
          <Field
            label="Your name"
            value={name}
            onChangeText={setName}
            maxLength={100}
            autoComplete="name"
            error={submitted ? errors.name : null}
          />
        )}
        <Field
          label="Email"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          error={(submitted ? errors.email : null) ?? serverFieldError('email')}
        />
        <Field
          label="Password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete={signingUp ? 'new-password' : 'current-password'}
          hint={
            signingUp
              ? 'At least 10 characters. A short sentence works well.'
              : undefined
          }
          error={
            (submitted ? errors.password : null) ?? serverFieldError('password')
          }
          onSubmitEditing={submit}
        />
        <ErrorText
          error={
            error && !(error instanceof ApiError && error.fields) ? error : null
          }
        />
        <Button
          title={
            busy ? 'Please wait…' : signingUp ? 'Create account' : 'Sign in'
          }
          onPress={submit}
          disabled={busy}
        />
        <Button
          title={signingUp ? 'I already have an account' : 'Create an account'}
          variant="secondary"
          onPress={switchMode}
        />

        <View style={styles.divider}>
          <View style={[styles.line, { backgroundColor: theme.border }]} />
          <Text style={{ color: theme.muted }}>or</Text>
          <View style={[styles.line, { backgroundColor: theme.border }]} />
        </View>
        <Button
          title="Continue with Google"
          variant="secondary"
          onPress={signInWithGoogle}
        />
        {googleError && (
          <Text style={{ color: theme.negative }}>{googleError}</Text>
        )}
      </Screen>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  divider: { flexDirection: 'row', alignItems: 'center', gap: spacing.s },
  line: { flex: 1, height: 1 },
});
