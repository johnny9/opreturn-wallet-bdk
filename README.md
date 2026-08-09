# OP_RETURN Wallet

An Android wallet focused on publishing one conservative UTF-8 message in a zero-value Bitcoin `OP_RETURN` output while returning the remaining wallet value as change.

This is a new project with its own history. Its Android/BDK foundation follows the maintained [`bitcoindevkit/devkit-wallet` `variant/2.0`](https://github.com/bitcoindevkit/devkit-wallet/tree/variant/2.0) reference, not the legacy BDK 0.8 wallet.

## Transaction design

The default transaction is:

```text
wallet input(s)
  ├─ 0 sats       OP_RETURN <exact UTF-8 bytes>
  └─ remainder    fresh internal wallet change

fee = inputs - outputs
```

Optional anchor modes add an ordinary, spendable output to a fresh wallet address or a validated recipient. The default anchor is 1,000 sats. Production code never enables BDK's `allowDust` option.

A hidden debug-only Regtest mode can consume one manually selected UTXO without change. It is compiled out of release builds, limited by the absolute fee cap, and presents a red full-input fee warning.

## Implemented MVP

- BIP39 create, recovery display, full-phrase verification, and restore flows
- Separate BIP84 external and internal descriptors
- BDK SQLite wallet-state persistence
- AES-GCM seed encryption with a non-exportable Android Keystore key
- Optional biometric or device-credential unlock
- Screenshot blocking on restore, recovery phrase, verification, and unlock screens
- Regtest, Signet, Testnet4, and explicitly enabled mainnet
- Esplora full scan, incremental sync, fee estimates, broadcast, and confirmation polling
- Exact strict UTF-8 encoding, live byte count, hex preview, and an 80-byte maximum
- Standard, anchor-to-self, and anchor-to-recipient modes
- RBF signaling and previewed fee bumping for eligible pending message transactions
- Minimum 1 sat/vB, absolute and percentage fee caps
- Mandatory public/permanent acknowledgment before signing
- Signed-transaction input/output commitment verification immediately before broadcast
- Full-wallet sweep to one network-validated external address, with no change and fee deducted
- Message history, decoded payload, status, block height, and network explorer links

## Project layout

```text
app/src/main/java/org/opreturnwallet/bdk/
  wallet/       BDK lifecycle, descriptors, persistence, balances and history
  chain/        Esplora scanning, synchronization, fees and broadcast
  message/      strict UTF-8 payload and OP_RETURN script handling
  transaction/  modes, dust/fee policy, construction, preview and final checks
  storage/      Android Keystore seed encryption and non-secret DataStore metadata
  ui/           Compose screens and state management
```

See [Architecture](docs/ARCHITECTURE.md), [Security](docs/SECURITY.md), and [Testing](docs/TESTING.md).

## Build

Requirements:

- Android SDK 37
- Java 17 toolchain (Gradle can provision one through Foojay)
- Internet access for Maven dependencies

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleMainnetTrial
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

`assembleMainnetTrial` produces a separately installable APK at
`app/build/outputs/apk/mainnetTrial/app-mainnetTrial.apk`. Its application ID is
`org.opreturnwallet.bdk.mainnettrial`, so Android isolates its wallet database, preferences,
Keystore entries, and files from the ordinary Signet-first installation. The build only permits
Mainnet wallet creation, keeps the explicit real-bitcoin acknowledgment, labels itself as a trial,
and compiles out the dangerous Regtest consume mode.

BDK is pinned to `org.bitcoindevkit:bdk-android:2.3.1`, the newest stable release compatible with the maintained 2.x reference baseline when this project was created. A future BDK 3.x upgrade should be handled as an explicit migration.

## Network defaults

| Network | Esplora endpoint | Availability |
|---|---|---|
| Regtest | `http://10.0.2.2:3002` | Default emulator-local endpoint |
| Signet | `https://blockstream.info/signet/api/` | Enabled |
| Testnet4 | `https://mempool.space/testnet4/api/` | Enabled |
| Mainnet | `https://mempool.space/api/` | Hidden until explicitly enabled |

Public endpoints are a privacy compromise: they can correlate the addresses queried for a wallet. Custom Esplora configuration is intentionally deferred to a later release.

## Release acceptance

The source and automated policy tests cover the MVP behavior. Before distributing an APK, complete the funded-device Regtest and Signet acceptance procedures in [Testing](docs/TESTING.md), including confirmation and recovery rediscovery. Never test mainnet with funds you cannot afford to lose.

## Sweeping funds off the phone

The Home screen can construct a sweep to an address controlled by another wallet. A sweep:

- synchronizes immediately before construction;
- spends every currently available wallet UTXO;
- creates exactly one external recipient output and no change or OP_RETURN output;
- deducts the mining fee from the recipient amount;
- rejects an address owned by this wallet or from the wrong network;
- retains RBF, absolute-fee, percentage-fee, dust, preview, and signed-transaction checks;
- requires typing `SWEEP` before signing and broadcasting.

A sweep only moves currently spendable funds. It does not erase the encrypted recovery phrase,
protect against later incoming payments, or replace confirmation in the receiving wallet.

## Bumping a pending message fee

Pending outgoing message transactions with opt-in RBF and internal wallet change expose a
`Bump fee` action. The wallet synchronizes first, builds the replacement with BDK's fee-bump
builder, and requires a second preview and acknowledgment before signing. Every original input
must remain present, any newly selected input must already be confirmed, and every non-change
output must retain its exact value and script. Only internal change may shrink or disappear.

The replacement must pay both a higher fee rate and at least one additional satoshi per
replacement virtual byte. The normal absolute and percentage fee caps still apply. Sweeps and
other transactions without replaceable wallet change are intentionally not eligible.

## License

Apache-2.0. See [LICENSE](LICENSE).
