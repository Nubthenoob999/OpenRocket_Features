![OpenRocket banner](.github/banner.png)

**Vanilla OpenRocket** -->

![Build Status](https://github.com/openrocket/openrocket/actions/workflows/build.yml/badge.svg)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![GitHub release](https://img.shields.io/github/release/openrocket/openrocket.svg)
[![Github Releases (by release)](https://img.shields.io/github/downloads/openrocket/openrocket/latest/total.svg)](https://GitHub.com/openrocket/openrocket/releases/)
[![Read the Docs](https://readthedocs.org/projects/openrocket/badge/?version=latest)](https://openrocket.readthedocs.io/en/latest/)
[![snap release](https://snapcraft.io/openrocket/badge.svg)](https://snapcraft.io/openrocket)
![Chocolatey release](https://img.shields.io/chocolatey/v/openrocket)
[![Crowdin](https://badges.crowdin.net/openrocket/localized.svg)](https://crowdin.com/project/openrocket)
[![Join our Discord server!](https://img.shields.io/discord/1073297014814691328?logo=discord)](https://discord.gg/qD2G5v2FAw)

This fork is dedicated to Project Imperia. It aims to reconcile some known and personal gripes with base OpenRocket from the lack of a native Monte Carlo Simulations as well as simulation accuracy. 

First a custom Monte Carlo wrapper was implemented to nativley interface with the simulation tab. The regular plugin can be found at: [Plugin Link](https://github.com/NCSU-High-Powered-Rocketry-Club/OpenRocket-Monte-Carlo)

The main objective is to introduce a fully physics based Pathline Reduced Order Model to better calculate various coefficents for a given vehicle configuration. A high level overview is that a ray is shot through the given fluid domain in which the base OpenRocket Configuration is placed in. Then using physics based equations, expereimental derived equations and correlations, and other techniques, the fluid properties are intergated along that ray. 

Since this a work-in-progress [Technical Documentation]() will be continually updated for the under the hood math. Moreover, this README will be updated once the ROM and all other codes are verified to be accurate for predicting altitude, and the overall ascent profile.

All credit goes to the original/current creators and maintainers of OpenRocket!

### ✨ OpenRocket Contributors
- [Sampo Niskanen](https://github.com/plaa) - Original developer
- [Doug Pedrick](https://github.com/rodinia814) - RockSim designs, printing
- [Kevin Ruland](https://github.com/kruland2607) - Android version
- [Bill Kuker](https://github.com/bkuker) - 3D visualization
- [Richard Graham](https://github.com/rdgraham) - Geodetic computations
- Jason Blood - Freeform fin set import
- [Boris du Reau](https://github.com/bdureau) - Internationalization
- [Daniel Williams](https://github.com/teyrana) - Pod support, maintainer
- [Joe Pfeiffer](https://github.com/JoePfeiffer) - Maintainer
- [Billy Olsen](https://github.com/wolsen) - Maintainer
- [Sibo Van Gool](https://github.com/SiboVG) - RASAero file format, 3D OBJ export, dark theme, maintainer
- [Neil Weinstock](https://github.com/neilweinstock) - Tester, icons, forum support
- [H. Craig Miller](https://github.com/hcraigmiller) - Tester

You can view the full list of contributors [here](https://github.com/openrocket/openrocket/graphs/contributors).

### 🌍Translators
- Tripoli France
- Tripoli Spain
- Stefan Lobas / ERIG
- Mauro Biasutti
- Sky Dart Team / Ruslan V. Uss
- Vladimir Beran
- Polish Rocketry Society / Łukasz & Alex Kazanski
- Sibo Van Gool
- Mohamed Amin Elkebsi
- Oleksandr Hladin

## 📜 License

OpenRocket is proudly open-source under the [GNU GPL](https://www.gnu.org/licenses/gpl-3.0.en.html) license. Feel free to use, study, and extend.

---
 
⭐ Please give OpenRocket a star if you find OpenRocket useful, and spread the word! ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=openrocket/openrocket&type=Date)](https://star-history.com/#openrocket/openrocket&Date)
