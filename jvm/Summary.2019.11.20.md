
- Asynchronous doesn't mean parallel.
    - The first copy and fetch version is asynchronous, yet sequential.
    - No mutual exclusion needed
        - No concurrent access - sequential processing!
        - No visibility issues - HB between asynchronous operation and completion handler ensures visibility
    - Sequential with multiple threads, no concurrency.
        
- However, asynchronous "opens the door" to parallel 
    - When using synchronous APIs, parallelization means calling those APIs on different threads
        - Because they block!
       
    - When using asynchronous APIs, parallelization is achieve by starting multiple operations
    (on the same thread) without synchronizing with termination.
    - Parallel with multiple threads, concurrency exists!
        - So we need to ensure
            - Correct write-read visibility between threads
            - Correct mutual exclusion when mutating data
            
- Beware
    - of synchronous exceptions
        - Should not go unnoticed
        - should result in calling the external completion handler
    - only call external completion handler when all internal operations are completed
        - I.e. no pending operations exist