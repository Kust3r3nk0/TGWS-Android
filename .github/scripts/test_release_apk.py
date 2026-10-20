import unittest
from verify_release_apk import verify_metadata, verify_certificate, PACKAGE, PUBLISHED_DEBUG

class ProductionGateTest(unittest.TestCase):
    def test_stable_identity_and_api36(self):
        valid=f"package: name='{PACKAGE}' versionCode='17' versionName='0.4.0'\nsdkVersion:'36'\n"
        verify_metadata(valid,'', '0.4.0',17)
        for bad in [valid+'application-debuggable\n',valid.replace("'17'","'16'"),valid.replace("'36'","'35'"),valid.replace(PACKAGE,'other.package')]:
            with self.assertRaises(ValueError):verify_metadata(bad,'','0.4.0',17)
        with self.assertRaises(ValueError):verify_metadata(valid,'A: android:debuggable(0x123)=(type 0x12)0xffffffff','0.4.0',17)
    def test_certificate_must_match_trusted_non_debug_key_and_integrity(self):
        output='Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: '+'a'*64+'\n'
        self.assertEqual('a'*64,verify_certificate(output,'a'*64))
        for bad,cert in [(output,None),(output,'b'*64),(output.replace('true','false'),'a'*64),(output.replace('a'*64,PUBLISHED_DEBUG),PUBLISHED_DEBUG),(output+'Signer #1 certificate DN: CN=Android Debug\n','a'*64)]:
            with self.assertRaises(ValueError):verify_certificate(bad,cert)
