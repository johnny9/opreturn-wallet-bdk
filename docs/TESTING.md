# Testing

## Automated local gates

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug
./gradlew :app:lintMainnetTrial
./gradlew :app:assembleDebug
./gradlew :app:assembleMainnetTrial
```

Unit coverage includes:

- empty, one-byte, exactly-80-byte, and 81-byte payloads;
- multibyte UTF-8 counts;
- malformed Unicode rejection;
- embedded newline preservation and exact hex;
- direct and `PUSHDATA1` OP_RETURN parsing;
- anchor dust thresholds;
- absolute and percentage fee caps;
- sensitive model string representations remain redacted.

The always-safe Android test verifies address/network mismatch rejection. Live transaction tests are opt-in because they require a funded wallet and can broadcast.

## Regtest contract tests

`RegtestTransactionInstrumentedTest` expects an emulator-local Esplora service at `10.0.2.2:3002` and a disposable, confirmed funded wallet. It verifies:

- at least one input;
- exactly one zero-value OP_RETURN;
- exact payload bytes;
- standard change;
- anchor plus change;
- explicit RBF signaling;
- an actual higher-fee replacement when broadcast testing is authorized;
- no OP_RETURN entry in BDK's output set;
- balance reduction by replacement fee.
- sweep selection of every available UTXO;
- exactly one external sweep output with no change or OP_RETURN;
- sweep input value equaling recipient value plus fee;
- rejection of a sweep destination owned by the same wallet.

Provide live-test secrets through a local Android Studio instrumentation configuration or another non-versioned secret provider. Do not put recovery phrases in shell history, project files, CI variables for untrusted jobs, logs, or chat.

## Signet first-release acceptance

`SignetLifecycleInstrumentedTest` is an opt-in, spending acceptance test. Use a disposable, low-value Signet wallet and explicitly authorize the run. It:

1. full-scans the funded wallet;
2. publishes an ASCII message;
3. publishes a Unicode message;
4. waits up to 30 minutes for both confirmations;
5. loads the same SQLite state to simulate restart;
6. restores into a new SQLite database;
7. full-scans and verifies both decoded messages are rediscovered.

Before a release, also manually verify screenshot blocking, biometric enrollment behavior, background/foreground behavior, explorer links, offline startup, and the visible fee/permanence warnings on a physical device.

## Mainnet

The isolated `mainnetTrial` build exists for an explicitly accepted, low-value manual trial. It is
not a production release. Before funding it, verify its distinct application ID and label, create a
fresh recovery phrase, verify that backup, and confirm the normal Signet installation remains intact.

Before broadcasting a Mainnet message or sweep, manually verify:

- the synchronized balance and transaction history;
- the exact destination network and full address on both devices;
- all previewed inputs, output value, fee, fee rate, and zero change for a sweep;
- a signed transaction commitment match;
- receipt and confirmation in the external wallet before removing local wallet data.

Use only funds the tester can afford to lose. External security review and reproducible release
signing remain required before calling any build production-ready.
