# manmem
Framework to provide managed memory in the JVM.

## Roadmap

### Major
* build single-threaded version with pre-emption [ok]
* make framework threadsafe, let disk operators run asynchronously
* build utilties on top of managed memory, such as sorting and maps

### Minor
* add hierarchical memory managers to manage quotas of memory
* assign priorities to memory segments that will be respected in the pre-emption
* tidy up the interface of objects that clients interact with

### Optional
* support memories chunks of different sizes
