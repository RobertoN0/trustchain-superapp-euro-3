# Token selection

Offline money is represented as tokens, withdrawn from the intermediary while online. For any transaction, a set of tokens needs to be selected that fulfills the required amount to be payed. Since there are various algorithms, strategies, to select tokens, each required under different circumstances, the [strategy](https://refactoring.guru/design-patterns/strategy) design pattern was adopted, allowing the algorithms to be used interchangeably. Hence, each algorithm implements the `SelectionStrategy` interface. The different strategies are described below.

## MPT Selection
MPT selection is selection based on a Merkel Patrica Trie (MPT) as described in [this](https://d197for5662m48.cloudfront.net/documents/publicationstatus/250191/preprint_pdf/a6b5b07b853af33d6d6f1562eb27607c.pdf) paper. The aim is to limit the probability of the sender sending already spent tokens by selecting them using a seed provided by the receiver of the transaction. The seed is provided through the QR code. The MPT data structure allows the receiver to verify that the sender executed the selection algorithm correctly, based on the provided seed.

## Random Selection
This is a simple strategy that randomly selects a subset of tokens among the unspent ones. Unspent tokens are stored in the token database and markt spent as soon as an agreement block is received by the receiver party of a transaction.

## Double Spend Selection
This strategy deliberately selects tokens that have already been spent in order to demonstrate the probabilistic double spending prevention mechanism implemented through Bloom Filter broadcasting.

## Forged Token Selection
This strategy modifies tokens to be send with a transaction in order to demonstrate the proof of authenticity mechanism implemented through a signature from the intermediary. If a token is altered, for example, its value is changed, the receiving party should be able to notice this by checking the sign of the token.