SimpleDHT
=======

SimpleDHT is an Android app that implements a simplified version of a Distributed Hash Table (DHT) based on Chord.
It handles new node-joins to form a Chord ring that supports insert, query and delete requests in a distributed fashion according to the Chord protocol. The SHA-1 hash function is used to generate keys for the nodes and the data.

The simplified version of Chord implemented in this project includes:

1. ID space partitioning/re-partitioning.

2. Ring-based routing.

3. Node joins.

It does not implement finger tables and finger-based routing and does not handle node leaves/failures or concurrent node joins.

For the query and delete requests, two special strings for the selection parameter are recognized:

1. "*" - query: returns all the <key, value> pairs stored in the entire DHT.
       - delete: deletes all the <key, value> pairs stored in the entire DHT.

2. "@" - query: returns all the <key, value> pairs stored in the local partition of the node (that receives this "@" query).
       - delete: deletes all the <key, value> pairs stored in the local partition of the node (that receives this "@" delete).

