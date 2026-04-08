<div align="center">

<img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/TAXI.png" alt="Taxi App Icon" width="12%" />

#  Taxi App
### A real-time ride-hailing app for passengers and drivers

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2021+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%26%20Database-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Maps](https://img.shields.io/badge/Google-Maps%20SDK-4285F4?style=flat-square&logo=google-maps&logoColor=white)](https://developers.google.com/maps)
[![Material Design](https://img.shields.io/badge/UI-Material%20Design-757575?style=flat-square&logo=material-design&logoColor=white)](https://material.io/)
[![Status](https://img.shields.io/badge/Status-In%20Development-orange?style=flat-square)](#)

</div>

---

## Overview

**Taxi App** is a native Android ride-hailing application built with Kotlin and Firebase, developed by [AhmadALSaffan](https://github.com/AhmadALSaffan). It serves both **passengers** and **drivers** with dedicated interfaces — passengers can book rides and track their trip in real time, while drivers can accept requests and manage their rides. The app is currently under active development.

---

## Screenshots

| Home (Passenger) | Home (Driver) | Prices | Trip Details | Finish Ride |
|:---:|:---:|:---:|:---:|:---:|
| <img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/home_TAXI%20APP.jpg" width="180px"> | <img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/home_driver_TAXI%20APP.jpg" width="180px"> | <img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/Prices_TAXI%20APP.jpg" width="180px"> | <img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/trip_details_TAXI%20APP.jpg" width="180px"> | <img src="https://ik.imagekit.io/qeitebnxx/TAXI%20APP/finish_ride_TAXI%20APP.jpg" width="180px"> |

---

## Features

### 🧍 Passenger
- 📍 **Book a Ride** — Select pickup and destination on an interactive map
- 💰 **Fare Estimation** — View pricing details before confirming a trip
- 🗺️ **Live Trip Tracking** — Track driver location in real time on the map
- 🧾 **Trip Details** — Full summary of the ride including route and cost
- ✅ **Ride Completion** — Clear finish-ride flow with trip recap

### 🚗 Driver
- 📲 **Ride Requests** — Receive and accept incoming passenger requests
- 🗺️ **Navigation View** — Dedicated driver home screen with map integration
- 📊 **Trip Management** — Manage active and completed rides

### ⚙️ General
- 🔐 **Authentication** — Secure login and registration via Firebase Auth
- 🔄 **Real-Time Sync** — Live updates powered by Firebase Realtime Database
- 🎨 **Material Design UI** — Clean, modern interface with intuitive layouts
- 🔔 **Push Notifications** — Real-time ride request alerts via Firebase Cloud Messaging

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML Layouts + Material Design |
| Maps | Google Maps SDK for Android |
| Auth | Firebase Authentication |
| Realtime Database | Firebase Realtime Database |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Cloud | Firebase Cloud Services |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- A Firebase project with **Authentication** and **Realtime Database** enabled
- A **Google Maps API key**

### 1 — Clone the Repository

```bash
git clone https://github.com/AhmadALSaffan/TAXI-APP.git
cd TAXI-APP
```

### 2 — Add Google Maps API Key

Add your Maps API key to `local.properties`:

> ⚠️ **Never commit `local.properties`.** It is already listed in `.gitignore`.

```properties
# local.properties
MAPS_API_KEY=your_google_maps_key_here
```

### 3 — Add Firebase Config

1. Go to your [Firebase Console](https://console.firebase.google.com/) → Project settings → Download **`google-services.json`**
2. Place it in `app/google-services.json`
3. Enable **Email/Password** sign-in and **Realtime Database** in your Firebase project

### 4 — Build & Run

```bash
./gradlew assembleDebug
```

Or press **▶ Run** in Android Studio.

---

## Project Status

This project is currently **under active development**. Planned upcoming features include:

- [ ] In-app payment integration
- [x] Push notifications for ride requests
- [ ] Driver ratings and reviews
- [ ] Ride history for passengers and drivers
- [ ] Admin dashboard

---

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create your feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "feat: add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## License

```text
This is my project — any help, feedback, or contributions are more than welcome!
```

---

<div align="center">
  Built with ❤️ by <a href="https://github.com/AhmadALSaffan">Ahmad AlSaffan</a>
</div>
