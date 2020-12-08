# Hegex Options DEX

##  [DEV] Deployable Hegic Contracts
https://github.com/stacksideflow/contracts-v888

## Development
Compile contracts (assumes you have `solc` installed):
```bash
truffle compile
```
Migrate:
```bash
truffle migrate
```

After migration is completed ABIs will be written to `/resources/public/contracts/build`, 
addresses will be written to shared `clj` file; ready to be consumed by frontend.

Start UI:
```bash
lein build-css
lein repl
(start-ui!)
# go to http://localhost:4177/
```


