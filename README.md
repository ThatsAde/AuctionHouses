# UnrealAuctions

An auction house plugin for Paper 1.21. Players list items for sale, buy from each other through a menu,
and post buy orders that other players can fill. Every purchase, listing and order goes through a single
transaction queue, so two players can never buy the same item and a failed database write never leaves
half a trade behind.

## Features

- **Listings:** sell the item in your hand with `/ah sell <price> [amount]`. Listings expire after a
  configurable time, and expired or cancelled items go to a collection box instead of being lost.
- **Buy orders:** ask for an item at a price you choose. The money is held in escrow when you create the
  order, paid to whoever fills it, and refunded if the order is cancelled or expires.
- **Menus:** paginated browsing, your own listings and orders, a confirmation step before every purchase,
  and a collection box.
- **Limits and fees:** a listing fee and an optional cancel fee, minimum and maximum prices, a blacklist of
  items, rate limits on listing and buying, and per-permission limits on active listings and open orders.
- **Storage:** SQLite by default, or MySQL through HikariCP. The schema is versioned and migrated on
  startup.

## How trades stay consistent

Each action is a `Transaction` that runs on one worker thread, in two phases:

1. `doExecute` checks and updates the database inside one SQL transaction, and rolls everything back if
   any step fails.
2. `executePostCommit` moves the money through Vault once the database change is committed.

Because there's only one worker, transactions never interleave: if two players click the same listing,
the second one sees it's already sold. The queue has a maximum depth and a circuit breaker, so a database
outage produces a single warning and a clear error for players instead of a flood of stack traces. Every
trade, including failed money transfers, is written to a transaction log.

## Known issues

### A purchase can complete without the buyer paying

`BuyItemTransaction` checks that the buyer has enough money, marks the listing as `SOLD` and commits that
change, and only then withdraws the money. If the withdrawal fails at that point, for example because
the buyer spent the money in the moment between the check and the withdrawal, the listing is already sold
and the buyer still receives the item. The plugin logs the failure as `ECONOMY_FAIL`, but it doesn't undo
the sale. The same applies when paying the seller fails: the buyer gets a refund, but keeps the item.

**The fix** is to take the money first, the way `CreateOrderTransaction` already does for buy orders:

1. Withdraw the price from the buyer. If that fails, stop with "not enough funds" before touching the
   listing.
2. Mark the listing as `SOLD` in the database. If that fails, for example because someone else bought it
   first, refund the buyer.
3. Pay the seller. If that fails, put the money in the seller's collection box (or retry later) instead of
   refunding the buyer, because the buyer already has the item.

### Vault is called from the worker thread

The money transfers run on the transaction worker, not on the main server thread. Most economy plugins
handle that fine, but Vault doesn't guarantee it, so an economy plugin that isn't thread safe could
misbehave. The fix is to run the Vault calls on the main thread and hand the result back to the worker.

## Commands and permissions

| Command | Description |
|---|---|
| `/ah` | Open the market |
| `/ah sell <price> [amount]` | List the item in your hand |
| `/ah collect` | Open your collection box |
| `/ah cancel` | Manage and cancel your listings |
| `/ah orders` | Browse buy orders |
| `/ah order <item> [amount]` | Create a buy order |
| `/ah admin stats` | Market statistics |
| `/ah admin reload` | Reload the config and messages |

| Permission | Default | Description |
|---|---|---|
| `unrealauctions.use` | everyone | Use the auction house |
| `unrealauctions.orders` | everyone | Create and fill buy orders |
| `unrealauctions.admin` | op | Admin commands |
| `unrealauctions.listinglimit.<n>` | none | Raise the active listing limit (configurable) |
| `unrealauctions.orderlimit.<n>` | none | Raise the open order limit (configurable) |

## Requirements

- Paper 1.21.4 or newer, on Java 21
- Vault, plus an economy plugin such as EssentialsX

## Building

```bash
mvn package
```

The plugin jar ends up in `target/`. Drop it in `plugins/`, start the server, and edit
`plugins/UnrealAuctions/config.yml` and `messages.yml` to taste.

## License

MIT, see [LICENSE](LICENSE).
