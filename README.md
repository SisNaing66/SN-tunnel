# ⚡ WARP TUNNEL - Fast & Secure Android WireGuard VPN

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin)
![WireGuard](https://img.shields.io/badge/Protocol-WireGuard-88171A?style=flat-square&logo=wireguard)
![Gradle](https://img.shields.io/badge/Gradle-8.5-02569B?style=flat-square&logo=gradle)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

**WARP TUNNEL** သည် Cloudflare WARP Network ကို အခြေခံ၍ တည်ဆောက်ထားသော Android VPN Application တစ်ခု ဖြစ်ပါသည်။ မြန်မာနိုင်ငံရှိ ISP Network များ၏ DPI (Deep Packet Inspection) အကန့်အသတ်များကို ကျော်ဖြတ်နိုင်ရန် အထူးပြင်ဆင်ထားသော Cloudflare Direct API နှင့် Custom Backup API များကို အသုံးပြုထားပါသည်။

---

## ✨ Features (ပါဝင်သော လုပ်ဆောင်ချက်များ)

- 🚀 **Dual Engine Support:** 
  - **Cloudflare Direct API:** Cloudflare WARP Server မှ တိုက်ရိုက် Config ရယူခြင်း။
  - **Custom PHP Backup API:** Direct API အလုပ်မလုပ်ချိန်များတွင် Backup Server များမှ Auto Fetch ပြုလုပ်ခြင်း။
- 🛡️ **WireGuard Native Integration:** WireGuard GoBackend SDK ကို တိုက်ရိုက် အသုံးပြုထားသဖြင့် မြန်ဆန်ပြီး Battery စားသုံးမှု သက်သာခြင်း။
- 🔑 **Reserved Bytes & IPv6 Compatibility:** ISP များတွင် Ping Timeout ဖြစ်ပွားခြင်းမှ ကာကွယ်ထားပါသည်။
- 🔗 **WireGuard URI Support:** `wireguard://` URI Link များကို အလွယ်တကူ Copy / Paste ပြုလုပ်၍ Config များ Import သွင်းနိုင်ခြင်း။
- 🌐 **Custom DNS Switcher:** Cloudflare (1.1.1.1) နှင့် Google (8.8.8.8) DNS များကို စိတ်ကြိုက် ပြောင်းလဲနိုင်ခြင်း။
- 📊 **Real-time Latency Ping:** ချိတ်ဆက်ထားစဉ် 1.1.1.1 သို့ Latency Ping ms အား ပုံမှန် စစ်ဆေးပေးခြင်း။
- 🌙 **Modern UI & Dark Mode:** Material Design 3 ရေးဆွဲပုံစံဖြင့် Dark Mode နှင့် Connection Logs ပါဝင်ခြင်း။

---

## 🛠️ Project Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 34 (Android 14)
- **Architecture:** Android Jetpack, Coroutines, Lifecycle Scope
- **Core Dependencies:**
  - `com.wireguard.android:tunnel` (WireGuard Android SDK)
  - `com.squareup.okhttp3:okhttp` (Network Requests)
  - `com.google.android.material:material` (UI Components)

---

## 📥 Building & Installation

### 1. Repository အား Clone လုပ်ရန်
```bash
git clone [https://github.com/nyeinkokoaung404/warp-tunnel.git](https://github.com/nyeinkokoaung404/warp-tunnel.git)
cd warp-tunnel
