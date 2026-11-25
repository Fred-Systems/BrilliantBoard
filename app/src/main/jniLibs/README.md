# Stockfish native binaries

Place the official Android binaries here before building a release:

```
arm64-v8a/libstockfish.so
x86_64/libstockfish.so
```

They are downloaded by `./gradlew downloadStockfish` or `bash scripts/setup-stockfish.sh`
and are intentionally **not committed** to git (GPL binary + ~30 MB per ABI).

Mark them executable: `chmod +x app/src/main/jniLibs/*/libstockfish.so`.
