### Creating server

**Improvement**
- Entering a URL is error prone:
  - Default server url is ws://SERVER:PORT/ws
  - It's not clear what is the different between entering host:port or ws://host:port
  - If I enter localhost:8443 test connection fails
  - If I enter `ws://localhost:8443` connection fails
  - If I enter `ws://localhost:8443` connection succeed

- The server name should be inferred from the URL, most of the time it will be the same as the server name

### Create a New Project Modal

**Bug**

- Trying to create a project with path `/wip/test` shows the error `Path contains invalid directory traversal patterns`

**Improvement**

- The modal is nicely responsive vertically but the content is not scrollable 
- Floating modals on mobile are not a very good pattern, the modal should be full screen 