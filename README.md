# symulacjaMongo - MongoDB sharded cluster event-driven simulator (Java)

This repository contains a simple event-driven Java simulator that models a MongoDB sharded cluster's concurrency and locking at a high level.

Key features
- Event-driven scheduler
- Configurable distributions for interarrival, service time, and transaction fanout
- Single-shard operations and multi-shard transactions
- Simplified lock model (shared read, exclusive write)
- Stats collection: response latencies, lock waiting times

Usage
- Build with Maven: mvn package
- Run the Simulation.main() or create a Simulation instance in code and configure distributions via provided setter methods.

Design notes and limitations
- This is a simplified model intended for experimentation. It approximates MongoDB locking semantics (shared/exclusive) and models transactions as coordinated multi-shard operations.
- To extend:
  - plug in more realistic lock acquisition/backoff policies
  - add two-phase commit coordinator delays
  - collect per-shard metrics and lock contention statistics

What I pushed
- A new branch feature/event-sim-java with a Maven Java project implementing the simulation code and README.

Next steps I can take
- Add more distribution types, deadlock simulation, and per-shard lock statistics.
- Integrate with unit tests and a runner that writes CSV output for deeper analysis.
