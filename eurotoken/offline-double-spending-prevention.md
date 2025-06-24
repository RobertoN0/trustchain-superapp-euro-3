# EURO 3 Team 3 — Offline Double-Spending Prevention
Previously, the EuroToken app made no distinction between online and offline payments, and no mechanism was in place to **prevent** double-spending. Instead, a form of *mitigation* was in place through a 'web of trust' mechanism that tracked known counterparties’ public keys.

With this project, inspired by the work described in [_Ad Hoc Prevention of Double-Spending in Offline Payments_](https://www.techrxiv.org/users/901695/articles/1277252/master/file/data/Ad%20Hoc%20Prevention%20of%20Double-Spending/Ad%20Hoc%20Prevention%20of%20Double-Spending.pdf?inline=true), we propose a new payment protocol specifically designed to prevent double-spending in **offline environments** (no access to Wi-Fi or mobile data).

## Key Features
- To be able to make offline payments, users must convert their online EuroTokens into **offline tokens**. This is done via a **withdrawal operation** through a trusted intermediary. Conversely, offline tokens can be redeemed and converted back into regular currency either by **explicitly redeeming them with the intermediary**, or by **completing an offline payment**, after which the recipient's funds are automatically translated back to the online representation.

- Offline money is represented as tokens with **unique spending identifiers**, allowing each transaction to be linked to a unique, verifiable unit of value. This prevents reuse of the same token across multiple payments.

- To detect double-spending in the absence of a global ledger, a **Bloom filter** is broadcast at the end of each transaction to nearby peers. This compact filter encodes the set of previously spent tokens and is later used to validate incoming payments.

- Instead of allowing the sender to freely choose which tokens to spend (which would enable strategic hiding), token selection is performed by traversing the payer’s **Merkle Patricia Trie (MPT)** in a fixed order determined by a **permutation of the trie’s nibbles**, seeded by the receiver. This deterministic process ensures the selection is non-manipulable and **verifiable** via Merkle proofs.
    


## Withdrawal and token representation

### Intermediary
The below screenshot shows the intermediary fragment. The objective of this fragment is to allow the user to comunicate with the **intermediary**. The latter should be a trusted third party that can exchange the **Online Account based** money with **Offline Token based** money. In our implementation this intermediary is represented by the **gateway** as we do not have access to a realistic third party. More in detail our conversion process is done by creating **proposal** blocks of **type withdrawal** with the gateway (similar to how the checkpoint blocks are created). These proposal block we create contain the total amount of money we are converting, together with the actual tokens that we are receiving/redeeming. For our simplified implementation there is never going to be an **agreement** withdrawal block as this would need the realistic intermediary. Functionality wise as the picture below suggests, we either input the value of money we want to transfer to tokens and press *SEND MONEY TO GATEWAY*, or we have unspent tokens that we don't need/want anymore so we press *REDEEM UNSPENT TOKENS*.

![Image](images\Intermediary_screenshot.jpg)

### Token Representation
The intermediary is responsible for creating **tokens** and to do so it needs to include the following information:
- **unique ID** - composed using peer's Id and a nonce (timestamp of creation)
- **value** of token in cents (5 cents in our case)
- **signature** of the intermediary
- creation date

These values are then used to check the validity of tokens during a transaction. More in detail, the token's id are included in a bloom filter so that when a transaction is happening, the receiver can check if the received tokens have not been already spent. Another important check is the signature; this allows the user to check that the tokens received actually come from the intermediary, and so they have not been forged. Finally the value of the token simply shows what value they represent and so what is the total of a transaction by summing their values.

