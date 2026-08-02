# Architecture

## Lifecycle

1. A BIP39 mnemonic is generated or parsed from user input.
2. BDK derives separate BIP84 external and internal descriptors.
3. The mnemonic is encrypted before metadata is committed.
4. BDK creates or loads a SQLite-backed wallet.
5. A restore performs an Esplora full scan; later refreshes use revealed-script sync.
6. Address revelation, chain updates, and transaction construction are persisted immediately.

Secret descriptors are reconstructed in memory from the encrypted mnemonic when the wallet opens. They are never stored in DataStore. BDK's SQLite persistence does not retain descriptor secret keys.

## Write pipeline

```text
text
  → strict UTF-8 encode and enforce 1..80 bytes
  → BDK TxBuilder.addData
  → optional normal anchor recipient
  → fee rate and explicit RBF sequence
  → finish PSBT and persist wallet changes
  → inspect exact inputs, outputs, OP_RETURN bytes, change and fee
  → user preview plus mandatory permanence acknowledgment
  → sign PSBT
  → compare signed transaction to approved input/output commitment
  → broadcast through Esplora
  → periodic wallet sync and confirmation display
```

The commitment check allows witness/signature data to change while requiring every input outpoint, output value, and output script to remain identical to the approved preview.

## Output policy

- Exactly one `OP_RETURN` output.
- Its value must be zero.
- Its decoded single-push bytes must exactly equal the strict UTF-8 payload.
- Standard mode requires at least one wallet-owned change output.
- Anchor modes require exactly one matching anchor and at least one separate wallet change output.
- Recipient addresses are parsed against the active BDK network.
- Spendable anchor outputs are checked against Bitcoin Core's default dust-threshold calculation.

## Fee policy

- Opt-in RBF sequence: `0xfffffffd`.
- User/backend fee rates are clamped to at least 1 sat/vB.
- Default maximum absolute fee: 100,000 sats.
- Default maximum fee percentage: 10% of selected input value.
- Anchor flows visibly warn when the fee exceeds the anchor value.
- Developer consume mode is Regtest/debug-only, selects exactly one UTXO, creates no change, and retains the absolute fee limit.
