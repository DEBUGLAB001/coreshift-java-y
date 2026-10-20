# CoreShift — Intelligent Data Center Workload Scheduler

CoreShift is a centralized operations dashboard designed to help infrastructure teams schedule, monitor, and manage computational workloads across server clusters. Users can submit computational tasks, establish prerequisite dependency chains, inspect compute worker capacities, and trigger automated scheduling cycles. The platform provides real-time visibility into queue rankings, worker utilization, and execution lifecycles to ensure data center resources operate smoothly.

## 1. Application Overview

### Core Functionality

- Workload Management: Register and inspect computational jobs with specific CPU core and memory requirements.
- Dependency Mapping: Link tasks into directed dependency workflows so child jobs wait until prerequisite tasks finish.
- Interactive Workflow Graph: Visualize job dependency networks on a visual canvas with color-coded status badges and directional dependency arrows.
- Priority Dispatch Queue: View eligible tasks ranked in real time by scheduling priority and retry recovery requirements.
- Worker Node Monitoring: Track live CPU and memory utilization across worker nodes to prevent hardware overload.
- Execution Simulation: Simulate task completions, trigger execution failures to test retry logic, and reassign running tasks between nodes.
- Scheduling Audit Log: Review an event timeline capturing cycle runs, job dispatches, failures, and cluster reconfigurations.

## 2. Debugging Challenge

CoreShift currently contains six intentional defects in its scheduling logic, resource management, and state tracking layers. These issues cause premature job execution, inverted queue priorities, worker memory overload, improper node selection, invalid job state transitions, and unsynchronized cluster records. Your goal is to identify and resolve these defects to restore dependable scheduling across the cluster.

### Reported Issues & Tasks:

Issue 1: Premature Readiness on Failed or Incomplete Prerequisites
- User Symptom: When an upstream prerequisite job fails and enters a retry state, its dependent child jobs are marked as ready and dispatched before the prerequisite finishes.
- Task: Ensure dependent tasks remain blocked until all of their required prerequisite jobs have completed successfully.

Issue 2: Retry Penalty Priority Inversion in Dispatch Queue
- User Symptom: When a high-priority job fails and attempts to retry, it is pushed behind lower-priority normal jobs in the scheduling queue instead of receiving priority recovery.
- Task: Recalculate retrying job priorities so that failed jobs receive a priority boost and execute ahead of regular workloads to prevent pipeline stalls.

Issue 3: Unbounded Memory Allocation Past Physical Capacity
- User Symptom: A worker node with limited remaining memory accepts jobs that exceed its physical capacity, causing total memory utilization to rise beyond 100%.
- Task: Enforce worker memory limits by checking requested memory against actual available memory rather than total capacity.

Issue 4: Inverse Worker Selection in Cluster Load Balancing
- User Symptom: When dispatching new workloads, the scheduler routes jobs to the most congested worker node instead of choosing an idle or lightly loaded node.
- Task: Update the load-balancing logic so the scheduler always selects the worker with the lowest current workload score.

Issue 5: Unauthorized Cancellation of Terminal Completed Workloads
- User Symptom: Clicking the cancel action on a job that has already completed successfully changes its status to cancelled, corrupting historical records and dependency tracking.
- Task: Prevent completed jobs from being cancelled, ensuring terminal states remain protected against invalid state transitions.

Issue 6: Stale Worker Capacity Persistence on Workload Reassignment
- User Symptom: After reassigning a running job to a new worker, refreshing the dashboard or inspecting worker metrics shows that the new worker's resource reservations remain unchanged.
- Task: Ensure that reassigning a workload immediately updates and saves the target worker's allocated resources so cluster metrics stay consistent.

## 3. Expected Behavior After Fixing Bugs

1. Downstream dependent jobs remain blocked whenever any prerequisite job is pending, running, or retrying.
2. Failed jobs receive boosted scheduling priority on retry, ensuring prerequisite pipelines recover ahead of new workloads.
3. Worker nodes reject workload allocations when the requested memory exceeds their remaining available capacity.
4. The load balancer selects the worker node with the lowest current utilization to distribute workloads evenly.
5. Jobs that have reached the completed state cannot be cancelled and reject invalid cancellation requests with a clear error.
6. Reassigning a running job immediately updates both the job record and the target worker's persisted resource reservations.

When all six defects are resolved, running the test command `tests/run_tests.sh` will report all tests passing with an exit code of 0.
