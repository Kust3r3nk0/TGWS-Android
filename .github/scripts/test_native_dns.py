import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('native_dns', Path(__file__).with_name('verify_native_dns.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativeDnsTest(unittest.TestCase):
    def check(self, entries):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                for name, data in entries.items():
                    archive.writestr(name, data)
            return native.verify(apk)

    def test_native_client_and_system_dns_are_allowed(self):
        self.assertEqual(2, self.check({'classes.dex': b'android/net/DnsResolver okhttp3/OkHttpClient',
                                      'classes2.dex': b'NetworkAccess web.telegram.org'}))

    def test_test_fixture_accidentally_packaged_in_secondary_dex_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check({'classes.dex': b'app', 'classes2.dex': b'Lexample/CloudflareDns;'})

    def test_doh_dependency_in_main_dex_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check({'classes.dex': b'Lokhttp3/dnsoverhttps/DnsOverHttps;'})

    def test_retired_endpoint_is_rejected_without_printing_config(self):
        with self.assertRaisesRegex(ValueError, '^Retired Cloudflare DoH code is present in APK DEX$'):
            self.check({'classes.dex': b'https://cloudflare-dns.com/dns-query'})

    def test_missing_dex_is_not_an_empty_success(self):
        with self.assertRaises(ValueError):
            self.check({'assets/example.txt': b'content'})


if __name__ == '__main__':
    unittest.main()
