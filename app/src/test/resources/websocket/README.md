`localhost.p12` is a public, test-only self-signed RSA certificate and private key.
It is valid for the DNS name `localhost`, from 2020-01-01 to 2119-12-08.
The password is `test-only-password`. This resource is under `src/test` and is
never packaged into the APK. Tests trust this certificate explicitly; production
uses the platform trust store and hostname verification.

To regenerate with JDK keytool:

```sh
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
  -dname CN=localhost -ext SAN=dns:localhost -startdate 2020/01/01 -validity 36500 \
  -storetype PKCS12 -keystore localhost.p12 -storepass test-only-password \
  -keypass test-only-password -noprompt
```
