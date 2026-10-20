# CoreShift

## Description

CoreShift is a lightweight data-center workload orchestration and resource scheduling application designed to model job dependencies, allocate worker resources, enforce execution lifecycles, and prioritize retries for failed tasks. The backend is implemented with Java and Spring Boot (`CoreShiftApplication.java`), exposing REST endpoints through `SchedulingApiController.java` for jobs, compute workers, priority queues, execution events, and workflow DAG visualization. Data persistence is managed by an in-memory repository with defensive snapshot isolation in `SchedulingStateRepository.java`, which provides deterministic scenario initialization and reset capabilities. Core algorithmic services reside in `service/`: `DependencySchedulerService.java` performs directed acyclic graph (DAG) cycle detection and evaluates dependency readiness; `RetryQueueService.java` recalculates priority and manages the dispatch queue; `ResourceAllocationService.java` enforces CPU and memory limits; `WorkerLoadBalancerService.java` scores worker utilization to select candidate nodes; `JobLifecycleService.java` enforces state transition rules; and `SchedulingOrchestratorService.java` executes scheduling cycles and job reassignments. The frontend provides a real-time operations dashboard in `index.html` with styling in `css/style.css` and dynamic canvas DAG rendering in `js/app.js`.

## Repository Structure

```
.
├── pom.xml                               # Maven project configuration and dependencies
├── challenge.json                        # Platform challenge metadata and runtime ports
├── start.sh                              # Application launch and source recompile watcher
└── src/
    └── main/
        ├── java/com/coreshift/
        │   ├── CoreShiftApplication.java # Spring Boot main entry point
        │   ├── config/
        │   │   └── WebConfig.java        # Static asset routing and preview path configuration
        │   ├── controller/
        │   │   └── SchedulingApiController.java # REST API endpoints for jobs, workers, DAG, queue
        │   ├── model/
        │   │   ├── Job.java              # Workload entity, priority, dependencies, requirements
        │   │   ├── Worker.java           # Compute node capacities, reservations, active workloads
        │   │   ├── JobStatus.java        # Lifecycle state machine enum
        │   │   ├── JobPriority.java      # Scheduling priority level definitions
        │   │   ├── ResourceRequirements.java # CPU core and RAM megabyte requirements
        │   │   └── SchedulingEvent.java  # Execution audit log event record
        │   ├── repository/
        │   │   └── SchedulingStateRepository.java # Snapshot-isolated datastore and scenario reset
        │   └── service/
        │       ├── DependencySchedulerService.java # DAG cycle detection and readiness evaluation
        │       ├── RetryQueueService.java          # Priority queue ordering and retry boost calculation
        │       ├── ResourceAllocationService.java  # Hardware capacity checks and reservation updates
        │       ├── WorkerLoadBalancerService.java  # Node load scoring and candidate selection
        │       ├── JobLifecycleService.java        # State machine transition enforcement
        │       └── SchedulingOrchestratorService.java # End-to-end dispatch cycle and job reassignment
        └── resources/
            ├── application.properties    # Server port (3000) and networking binding (0.0.0.0)
            └── static/
                ├── index.html            # Workload operations dashboard and DAG canvas view
                ├── css/style.css         # Dark glassmorphism styling and status indicators
                └── js/app.js             # Client state sync, canvas DAG rendering, and API actions
```

## Bugs and Bug Locations

1. Premature Readiness on Failed or Incomplete Prerequisites

- Bug location: `src/main/java/com/coreshift/service/DependencySchedulerService.java`, within `evaluateReadiness(List<Job> jobs)`.
- How to observe it: Reset the scenario and inspect the ready status of `job-transform-shared-a` while its prerequisite `job-extract-01` is in `FAILED` status.
- Failure: The dependency readiness traversal checks `if (dep == null || dep.getStatus() == JobStatus.PENDING) { ready = false; }`. Because `job-extract-01` is in `FAILED` status rather than `PENDING`, the check ignores the uncompleted prerequisite and evaluates downstream child jobs as ready.
- Expected: A job must only be marked ready when all of its prerequisites have reached `COMPLETED` status (`if (dep == null || dep.getStatus() != JobStatus.COMPLETED) { ready = false; }`).

2. Retry Penalty Priority Inversion in Dispatch Queue

- Bug location: `src/main/java/com/coreshift/service/RetryQueueService.java`, within `calculateEffectivePriority(Job job)`.
- How to observe it: Reset the scenario and observe the priority queue ordering for `job-extract-01` (base priority 8, retry count 1) compared to normal pending workloads.
- Failure: Retry priority adjustments are subtracted from base priority (`basePriority - (job.getRetryCount() * 5)`), reducing `job-extract-01` to effective priority 3 and demoting it behind lower-priority independent jobs.
- Expected: Retrying jobs must receive a priority boost (`basePriority + (job.getRetryCount() * 5)`) to yield effective priority 13, ensuring failed pipeline dependencies run before new workloads to prevent cluster starvation.

3. Unbounded Memory Allocation Past Physical Capacity

- Bug location: `src/main/java/com/coreshift/service/ResourceAllocationService.java`, within `hasSufficientCapacity(Worker worker, ResourceRequirements req)`.
- How to observe it: Allocate a job requiring 4096 MB of memory to `worker-gamma`, which has 8192 MB total memory and 6144 MB already in use (only 2048 MB available).
- Failure: The capacity check compares the requirement against total memory (`worker.getTotalMemoryMb() - req.getMemoryMb() < 0`) instead of factoring in `worker.getUsedMemoryMb()`, approving allocations that exceed physical worker limits.
- Expected: Memory allocations must be evaluated against remaining available capacity (`worker.getTotalMemoryMb() - worker.getUsedMemoryMb() < req.getMemoryMb()`) and rejected when insufficient.

4. Inverse Worker Selection in Cluster Load Balancing

- Bug location: `src/main/java/com/coreshift/service/WorkerLoadBalancerService.java`, within `selectBestWorker(Job job, List<Worker> workers)`.
- How to observe it: Execute a scheduling cycle with a 1-core, 2048 MB workload while `worker-alpha` is at 75% load and `worker-beta` is at 12.5% load.
- Failure: `selectBestWorker` selects the target node using `.max(Comparator.comparingDouble(this::calculateLoadScore))`, directing workloads to the most heavily congested worker instead of the least utilized worker.
- Expected: The load balancer must select the node with the lowest utilization score using `.min(Comparator.comparingDouble(this::calculateLoadScore))` to distribute workloads evenly.

5. Unauthorized Cancellation of Terminal Completed Workloads

- Bug location: `src/main/java/com/coreshift/service/JobLifecycleService.java`, within `isValidTransition(JobStatus current, JobStatus target)`.
- How to observe it: Send a `POST /api/jobs/job-prep-00/cancel` request or click "Cancel" on completed job `job-prep-00`.
- Failure: `isValidTransition` explicitly permits `case COMPLETED: if (target == JobStatus.CANCELLED) return true;`, allowing terminal completed jobs to be cancelled and corrupting workflow history.
- Expected: Terminal status `COMPLETED` must strictly prohibit transitions to any other state, rejecting cancellation attempts with HTTP 400 Bad Request.

6. Stale Worker Capacity Persistence on Workload Reassignment

- Bug location: `src/main/java/com/coreshift/service/SchedulingOrchestratorService.java`, within `reassignJob(String jobId, String targetWorkerId)`.
- How to observe it: Reassign an active running job to another worker via `POST /api/jobs/{id}/reassign` or the UI modal, then inspect `GET /api/workers` or refresh the page.
- Failure: In `reassignJob`, the target worker is allocated resources in memory, but `repository.saveWorker(targetWorker)` is omitted. The target worker's persisted state in the repository remains unchanged.
- Expected: The target worker's updated resource allocations must be persisted via `repository.saveWorker(targetWorker)` so subsequent API requests and allocation cycles observe accurate remaining capacity.
