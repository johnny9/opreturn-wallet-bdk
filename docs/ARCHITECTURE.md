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

## Sweep pipeline

```text
external destination plus fee rate
  → synchronize wallet
  → validate destination against active network and reject wallet-owned scripts
  → BDK TxBuilder.drainWallet and drainTo
  → explicit RBF sequence and fee rate
  → require every currently available UTXO as an input
  → require exactly one non-wallet recipient output
  → enforce recipient dust threshold and fee limits
  → preview destination, inputs, amount, fee and zero change
  → require typed SWEEP confirmation
  → sign and compare with approved input/output commitment
  → broadcast and monitor wallet transaction position
```

The fee is deducted from the single recipient output. A sweep never creates an OP_RETURN, anchor,
or wallet change output. A future payment to a previously revealed address is not included in an
already-broadcast sweep.

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

## Isolated Mainnet trial build

The `mainnetTrial` build type uses application ID `org.opreturnwallet.bdk.mainnettrial`. Android
therefore gives it storage and Keystore namespaces separate from the normal application. Repository
checks reject every network except Mainnet, the UI requires a real-bitcoin acknowledgment before
wallet creation or restore, and `ENABLE_DEBUG_CONSUME_UTXO` is false.
