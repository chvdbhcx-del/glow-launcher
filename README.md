# Glow Launcher

Лаунчер для Android в «стеклянном» розово-фиолетовом стиле: сетка иконок со страницами, док, строка поиска, папки, виджеты и темы в один тап.

Glow Launcher — форк [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) (снимок upstream-коммита `849fec9`, ноябрь 2026), который в свою очередь основан на AOSP Launcher3. Исходный код распространяется по лицензии Apache 2.0, см. [LICENSE.txt](LICENSE.txt) и [NOTICE](NOTICE). Оригинальный README Lawnchair сохранён в [README.lawnchair.md](README.lawnchair.md).

## Сборка

APK собирается в GitHub Actions на каждый push в `main` и на каждый pull request: вкладка **Actions** → нужный запуск → артефакт `glow-launcher-debug`.

Локально (нужны JDK 21 и Android SDK):

```bash
git clone --recursive https://github.com/chvdbhcx-del/glow-launcher
cd glow-launcher
./gradlew assembleLawnWithQuickstepGithubDebug
```

## Что отличается от Lawnchair

- Название, иконка и applicationId (`app.glowlauncher`).
- Розово-фиолетовый акцент по умолчанию.
- Отключены объявления и автообновления, которые приходят с серверов Lawnchair.
