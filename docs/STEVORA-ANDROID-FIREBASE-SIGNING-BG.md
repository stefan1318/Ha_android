# [STE-02] STEVORA Android — подписване и Firebase App Distribution

Статус: тестов workflow; **не е подписан или публикуван APK, докато съответният GitHub Actions run не завърши успешно**. Не публикувайте в Google Play от този тестов workflow.

## Какво правят системите

- Android Gradle подписва `fullRelease` APK с дългосрочния **собствен** STEVORA keystore.
- GitHub Actions държи пароли и ключови данни само в GitHub Secrets и ги подава на изолирания runner.
- Firebase App Distribution приема **вече подписания** APK и по избор го споделя с предварително определени тестери. Firebase **не генерира/подписва** APK вместо keystore.
- Package name: `com.stevora.companion`; variant: `fullRelease`; версията на APK се сверява с `2.1.0-full`. `minimalRelease` е различен package (`com.stevora.companion.minimal`) и няма да се качва чрез този workflow.

## Подготовка: Firebase Console

1. В съществуващ или нов Firebase проект регистрирайте **Android** app `com.stevora.companion`. Използвайте точния package name; не `.debug`, `.minimal` или оригиналния Home Assistant ID.
2. В **App Distribution** активирайте Android приложението. Копирайте **Firebase App ID** (формат `1:...:android:...`) — не Google API key и не Android package ID.
3. Настройте service account за CI с необходимото Firebase App Distribution разрешение; предоставете достъп само до целевия проект. Предпочитайте краткотрайни креденшъли, когато е възможно. За текущия workflow се използва service-account JSON, защитен в GitHub Secret.
4. По избор създайте тестерски групи в App Distribution и запишете техните алиаси, ако искате покани автоматично. Без групи приложението само се качва в App Distribution.

## GitHub → Repository Settings → Secrets and variables → Actions

Добавете следните **Repository secrets** (пази се извън Git и извън чата):

| Secret | Съдържание |
|---|---|
| `STEVORA_KEYSTORE_BASE64` | Base64 от постоянния release keystore `.jks`/`.keystore` |
| `STEVORA_KEYSTORE_PASSWORD` | Парола на keystore |
| `STEVORA_KEY_ALIAS` | Alias на release key |
| `STEVORA_KEY_PASSWORD` | Парола на конкретния ключ |
| `STEVORA_FIREBASE_SERVICE_ACCOUNT_BASE64` | Base64 от JSON файла на разрешения Firebase service account |
| `STEVORA_FIREBASE_APP_ID` | Firebase Android App ID |
| `STEVORA_FIREBASE_TESTER_GROUPS` | Незадължително: алиас или алиаси на тестерски групи, разделени със запетая |
| `STEVORA_FIREBASE_CONFIG_BASE64` | Незадължително за текущия build: Base64 от реалния `google-services.json`, ако се изисква FCM; трябва да съдържа `com.stevora.companion` |

**Никога не добавяйте keystore, private key, service account JSON, `google-services.json` с чувствителни настройки или пароли като tracked файлове.** Пазете сигурно offline копие на release keystore и неговите пароли: при загуба може да не успеете да обновявате съществуващи инсталации със същия подпис.

## Изпълнение

1. Завършете PR security/yamllint checks на точния commit.
2. GitHub → **Actions** → `STEVORA Companion 2.1 Full Release` → **Run workflow**; изберете проверения продуктов клон/commit (workflow трябва да е наличен за manual dispatch от default branch).
3. Включете `firebase_distribution=true` **само** ако желаете качване във Firebase и секретите са конфигурирани. В противен случай се изгражда само подписан APK като GitHub artifact.
4. Проверете успешни стъпки: `Build signed STEVORA full release`, `Verify signed full APK`, `Upload ... signed APK`, а при включено разпространение — `Publish signed full APK to Firebase App Distribution`.
5. Потвърдете Android package name, `apksigner verify --print-certs`, SHA256, Firebase release и инсталация на реално Android устройство.
6. Тест на **актуализация**: стар и нов APK трябва да използват **същия signing key и package ID**, иначе Android не позволява update върху предишната инсталация.

## Текущи ограничения

- Няма доказателство за успешно подписана и качена версия, докато CI не го потвърди. GitHub connector не дава четене на стойностите на Secrets.
- Отделният `stevora-signed.yml` изгражда **minimal** APK; за Firebase Full използвайте `stevora-full-release.yml`.
- Подписан APK/Firebase дистрибуция ≠ Google Play production release.
- STEVORA OS/API интеграцията и реалните тестове на независима инсталация остават отделни release gates.

Официални източници: https://developer.android.com/studio/publish/app-signing ; https://firebase.google.com/docs/app-distribution/android/distribute-cli ; https://firebase.google.com/docs/app-distribution/authenticate-service-account?platform=android
