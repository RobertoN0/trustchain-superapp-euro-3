# EURO 3 Team 3 — Offline Double-Spending Prevention
Previously, the EuroToken app made no distinction between online and offline payments, and no mechanism was in place to **prevent** double-spending. Instead, a form of *mitigation* was in place through a 'web of trust' mechanism that tracked known counterparties’ public keys.

With this project, inspired by the work described in [_Ad Hoc Prevention of Double-Spending in Offline Payments_](https://www.techrxiv.org/users/901695/articles/1277252/master/file/data/Ad%20Hoc%20Prevention%20of%20Double-Spending/Ad%20Hoc%20Prevention%20of%20Double-Spending.pdf?inline=true), we propose a new payment protocol specifically designed to prevent double-spending in **offline environments** (no access to Wi-Fi or mobile data).

## Key Features
- To be able to make offline payments, users must convert their online EuroTokens into **offline tokens**. This is done via a **withdrawal operation** through a trusted intermediary. Conversely, offline tokens can be redeemed and converted back into regular currency either by **explicitly redeeming them with the intermediary**, or by **completing an offline payment**, after which the recipient's funds are automatically translated back to the online representation.

- Offline money is represented as tokens with **unique spending identifiers**, allowing each transaction to be linked to a unique, verifiable unit of value. This prevents reuse of the same token across multiple payments.

- To detect double-spending in the absence of a global ledger, a **Bloom filter** is broadcast at the end of each transaction to nearby peers. This compact filter encodes the set of previously spent tokens and is later used to validate incoming payments.

- Instead of allowing the sender to freely choose which tokens to spend (which would enable strategic hiding), token selection is performed by traversing the payer’s **Merkle Patricia Trie (MPT)** in a fixed order determined by a **permutation of the trie’s nibbles**, seeded by the receiver. This deterministic process ensures the selection is non-manipulable and **verifiable** via Merkle proofs.
    