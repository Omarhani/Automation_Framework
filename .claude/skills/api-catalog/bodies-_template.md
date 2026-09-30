# <App> API - <area> (from the app's script)

> Read out of the app's script on <date> (build `<main.<hash>.js>`), **not called live**. A call is safe to build on
> only after the verification run described in `SKILL.md` (status ✅ there). Where this file and a ✅ client method
> differ, the Java is right.

`[R]` = read in the code, `[I]` = inferred. How the app sends a request (wrappers, fields an interceptor adds) is
noted once in `SKILL.md` → *How to call*. Key casing below is exactly what the app sends.

## Set-up / clean-up recipes

- **<Entity>**: create `<Controller/Action {body}>` → find its id with `<list call>` → delete `<call>`.

## <Controller>

### <Controller>/<Action> - POST
UI: <page → button → the component method that calls it>.
```
{ <key>: <type / example>,      // required? default? enum values?
  ... }
```
Reads: `<what the code reads from the answer - the fields a test can use to find the new id>`.
Client rules: `<validators, refusals shown by the app, date / time formats>`.

### <Controller>/<Action with no call site>
Constant only - the app never calls it. Body: not determined.

## Not determined
- <what could not be read, and where it would have to come from>
