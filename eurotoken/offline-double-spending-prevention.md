# EURO 3 Team 3 — Offline Double-Spending Prevention
Previously, the EuroToken app made no distinction between online and offline payments, and no mechanism was in place to **prevent** double-spending. Instead, a form of *mitigation* was in place through a 'web of trust' mechanism that tracked known counterparties’ public keys.

With this project, inspired by the work described in [_Ad Hoc Prevention of Double-Spending in Offline Payments_](https://www.techrxiv.org/users/901695/articles/1277252/master/file/data/Ad%20Hoc%20Prevention%20of%20Double-Spending/Ad%20Hoc%20Prevention%20of%20Double-Spending.pdf?inline=true), we propose a new payment protocol specifically designed to prevent double-spending in **offline environments** (no access to Wi-Fi or mobile data).

## Key Features
- To be able to make offline payments, users must convert their online EuroTokens into **offline tokens**. This is done via a **withdrawal operation** through a trusted intermediary. Conversely, offline tokens can be redeemed and converted back into regular currency either by **explicitly redeeming them with the intermediary**, or by **completing an offline payment**, after which the recipient's funds are automatically translated back to the online representation.

- Offline money is represented as tokens with **unique spending identifiers**, allowing each transaction to be linked to a unique, verifiable unit of value. This prevents reuse of the same token across multiple payments.

- To detect double-spending in the absence of a global ledger, a **Bloom filter** is broadcast at the end of each transaction to nearby peers. This compact filter encodes the set of previously spent tokens and is later used to validate incoming payments.

- Instead of allowing the sender to freely choose which tokens to spend (which would enable strategic hiding), token selection is performed by traversing the payer’s **Merkle Patricia Trie (MPT)** in a fixed order determined by a **permutation of the trie’s nibbles**, seeded by the receiver. This deterministic process ensures the selection is non-manipulable and **verifiable** via Merkle proofs.

## Offline Transaction
The transaction begins when the **receiver**, operating offline, creates a QR code. This offline QR code  contains the same information as its online version(public key, requested amount, name), but also includes a **seed**. This seed will be used by the sender to run the algorithm used to select which tokens to include in the transaction

Once the **sender** scans this QR code, the app presents a summary screen showing the transaction details. Here, the sender can choose between different behaviors:
-  normal, legitimate payment;
- a double-spending attempt (reusing previously spent tokens);
- or using **forged tokens** (not issued by the intermediary). These last two are included for demo purposes.

In all cases, the sender constructs a **Proposal Block** of type `OFFLINE_TRANSFER`.
The Proposal Block is then sent to the receiver, who proceeds to validate the transaction:
- Tokens are deserialized and their signatures are verified.
- The total value is checked to ensure it satisfies the requested amount.
- The receiver ensures that none of the tokens have been received before, this is done by consulting both the **local token database** and the current **Bloom filter**, which is populated with identifiers of previously received tokens and continuously updated through proximity broadcasts.

If all checks pass, the receiver responds with an **Agreement Block**, finalizing the transaction. At this point, the receiver updates their balance and:
- Adds the newly received tokens to their **local Bloom filter**
- Broadcasts the updated Bloom filter to nearby peers, helping prevent future double-spending.

![Offline Transaction Sequence Diagram](images/o)
## Tests
TO DO (say what tests we have done)
