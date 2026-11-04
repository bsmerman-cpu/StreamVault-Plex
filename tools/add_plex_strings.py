"""Add the small Plex UI vocabulary to every advertised Android locale."""
from pathlib import Path
from xml.sax.saxutils import escape

# Back | Sign out | Link Plex account | Choose a server | Playback unavailable
translations = {
    "values": "Back|Sign out|Link Plex account|Choose a server|Playback unavailable",
    "values-ar": "رجوع|تسجيل الخروج|ربط حساب Plex|اختر خادمًا|التشغيل غير متاح",
    "values-bg": "Назад|Изход|Свързване на Plex акаунт|Изберете сървър|Възпроизвеждането не е налично",
    "values-cs": "Zpět|Odhlásit se|Připojit účet Plex|Vybrat server|Přehrávání není dostupné",
    "values-da": "Tilbage|Log ud|Tilknyt Plex-konto|Vælg en server|Afspilning er ikke tilgængelig",
    "values-de": "Zurück|Abmelden|Plex-Konto verbinden|Server auswählen|Wiedergabe nicht verfügbar",
    "values-el": "Πίσω|Αποσύνδεση|Σύνδεση λογαριασμού Plex|Επιλέξτε διακομιστή|Η αναπαραγωγή δεν είναι διαθέσιμη",
    "values-es": "Volver|Cerrar sesión|Vincular cuenta de Plex|Elegir un servidor|Reproducción no disponible",
    "values-fa": "بازگشت|خروج|اتصال حساب Plex|انتخاب سرور|پخش در دسترس نیست",
    "values-fi": "Takaisin|Kirjaudu ulos|Yhdistä Plex-tili|Valitse palvelin|Toisto ei ole käytettävissä",
    "values-fil": "Bumalik|Mag-sign out|I-link ang Plex account|Pumili ng server|Hindi available ang playback",
    "values-fr": "Retour|Se déconnecter|Associer un compte Plex|Choisir un serveur|Lecture indisponible",
    "values-hi": "वापस|साइन आउट|Plex खाता लिंक करें|सर्वर चुनें|प्लेबैक उपलब्ध नहीं है",
    "values-hr": "Natrag|Odjava|Poveži Plex račun|Odaberi poslužitelj|Reprodukcija nije dostupna",
    "values-hu": "Vissza|Kijelentkezés|Plex-fiók összekapcsolása|Szerver kiválasztása|A lejátszás nem érhető el",
    "values-in": "Kembali|Keluar|Tautkan akun Plex|Pilih server|Pemutaran tidak tersedia",
    "values-it": "Indietro|Esci|Collega account Plex|Scegli un server|Riproduzione non disponibile",
    "values-iw": "חזרה|התנתקות|קישור חשבון Plex|בחירת שרת|ההפעלה אינה זמינה",
    "values-ja": "戻る|ログアウト|Plex アカウントを連携|サーバーを選択|再生できません",
    "values-ko": "뒤로|로그아웃|Plex 계정 연결|서버 선택|재생할 수 없음",
    "values-mk": "Назад|Одјави се|Поврзи Plex сметка|Избери сервер|Репродукцијата не е достапна",
    "values-ms": "Kembali|Log keluar|Pautkan akaun Plex|Pilih pelayan|Main balik tidak tersedia",
    "values-nb": "Tilbake|Logg ut|Koble til Plex-konto|Velg en server|Avspilling er ikke tilgjengelig",
    "values-nl": "Terug|Afmelden|Plex-account koppelen|Kies een server|Afspelen niet beschikbaar",
    "values-pl": "Wstecz|Wyloguj się|Połącz konto Plex|Wybierz serwer|Odtwarzanie niedostępne",
    "values-pt": "Voltar|Terminar sessão|Associar conta Plex|Escolher um servidor|Reprodução indisponível",
    "values-ro": "Înapoi|Deconectare|Conectează contul Plex|Alege un server|Redarea nu este disponibilă",
    "values-ru": "Назад|Выйти|Подключить учетную запись Plex|Выбрать сервер|Воспроизведение недоступно",
    "values-sk": "Späť|Odhlásiť sa|Pripojiť účet Plex|Vybrať server|Prehrávanie nie je dostupné",
    "values-sq": "Kthehu|Dil|Lidh llogarinë Plex|Zgjidh serverin|Riprodhimi nuk ofrohet",
    "values-sr": "Назад|Одјави се|Повежи Plex налог|Изабери сервер|Репродукција није доступна",
    "values-sv": "Tillbaka|Logga ut|Anslut Plex-konto|Välj en server|Uppspelning är inte tillgänglig",
    "values-th": "ย้อนกลับ|ออกจากระบบ|เชื่อมต่อบัญชี Plex|เลือกเซิร์ฟเวอร์|ไม่สามารถเล่นได้",
    "values-tr": "Geri|Çıkış yap|Plex hesabını bağla|Sunucu seç|Oynatma kullanılamıyor",
    "values-uk": "Назад|Вийти|Підключити обліковий запис Plex|Вибрати сервер|Відтворення недоступне",
    "values-vi": "Quay lại|Đăng xuất|Liên kết tài khoản Plex|Chọn máy chủ|Không thể phát",
    "values-zh": "返回|退出登录|关联 Plex 帐户|选择服务器|无法播放",
    "values-zh-rTW": "返回|登出|連結 Plex 帳戶|選擇伺服器|無法播放",
}

root = Path(__file__).resolve().parents[1]
app_res = root / "app/src/main/res"
settings_res = root / "feature/settings/src/main/res"
keys = ("plex_back", "plex_sign_out", "plex_link_account", "plex_choose_server", "plex_playback_unavailable")
for locale, row in translations.items():
    values = row.split("|")
    assert len(values) == len(keys), locale
    app_strings = app_res / locale / "strings.xml"
    content = app_strings.read_text(encoding="utf-8")
    additions = [f'    <string name="{key}">{escape(value)}</string>' for key, value in zip(keys, values)]
    additions += ['    <string name="plex_library">Plex</string>', '    <string name="plex_link_url">plex.tv/link</string>']
    for key in keys + ("plex_library", "plex_link_url"):
        assert f'name="{key}"' not in content, (locale, key)
    app_strings.write_text(content.replace("</resources>", "\n".join(additions) + "\n</resources>"), encoding="utf-8")

    settings_strings = settings_res / locale / "strings.xml"
    content = settings_strings.read_text(encoding="utf-8")
    assert 'name="settings_plex_library"' not in content
    settings_strings.write_text(content.replace("</resources>", '    <string name="settings_plex_library">Plex</string>\n</resources>'), encoding="utf-8")
