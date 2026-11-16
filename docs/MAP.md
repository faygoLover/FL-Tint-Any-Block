# FL Tint Any Block — карта

Версия на момент написания: 1.0.1 (16.11.2026). Факты по классам и миксинам — `docs/INDEX.md`.

## 1. Что делает и чего не делает

Оттенок и защита любого поставленного блока **без сущностей блока**: данные лежат в секциях чанка. Ведущий
красит валиком (`brush`) или командой `//shade`, защищает `//protect`; защищённый блок ломает только ведущий
(оператор 2+). Оттенок и защита переезжают вместе с блоком при копировании WorldEdit. Замена «усиленных» блоков
SecurityCraft, которые нагружали сервер.

За этим — не сюда:
- цветной свет и шейдеры — FL Render; здесь только множитель цвета граней блока при сборке чанка;
- защита регионов, права игроков — не этот мод: защита здесь поблочная и только «ломает ли не-ведущий».

Самостоятельный мод: RP Core не требует (при наличии — только регистрирует себя в списке модулей).

## 2. Как течёт работа

**Хранение.** `mixin/LevelChunkMixin` добавляет чанку поле (`data/ShadeChunk`) → `data/ShadeData` (секция по Y →
`data/ShadeSection`: палитра до 255 оттенков + индекс на блок, плюс биты защиты) → сохраняется вместе с чанком.
Блок сменился на другой — оттенок и защита с него снимаются.

**Покраска.** валик `item/BrushItem` (настройки — окно `client/BrushScreen` → `net/BrushConfigPacket`) или
`command/ShadeCommand` (`//shade set|clear|convert`, `//protect set|clear`; область — `command/Area`: коробка или
выделение WorldEdit `command/WorldEditSelection`; фильтр `solid` — `Structural`) → `data/Shades` (серверное API:
поставить оттенок или защиту, сохранить, разослать) → отмена — `command/UndoHistory`.

**Клиенту.** `net/ShadeSyncPacket` — оттенки чанка тем, кто его видит → `client/ShadeClient` → при сборке чанка
цвет квада умножается на оттенок: `mixin/client/BlockRendererEmbeddiumMixin` (Embeddium) или
`ModelBlockRendererMixin` (ваниль). Ведущему с валиком — контуры защищённых блоков (`client/ProtectionOverlay`).

**Защита.** `data/ProtectionEvents` (ломание, взрывы), `mixin/LevelMixin` (дроп до снятия блока),
`mixin/EndermanTakeBlockGoalMixin`.

**WorldEdit.** `compat/WorldEditHook`: оттенок и защита едут в NBT блока при копировании; что делать, когда
WorldEdit ставит блок без них, — режим игрока `compat/WeMode` (`//shade we keep|replace`).
**SecurityCraft.** `compat/SecurityCraftCompat` (отражением): `//shade convert` переводит его усиленные блоки в обычные с оттенком.

## 3. Карта пакетов

| Пакет | За что отвечает | С чего начать читать |
|---|---|---|
| `fltint` | вход, конфиг, цвета, фильтр «конструкция» | `FlTint`, `Config`, `ShadeColors`, `Structural` |
| `data` | хранение в чанке, серверное API, защита | `Shades` (вход для всего серверного), `ShadeSection`, `ShadeData`, `ShadeChunk`, `ProtectionEvents` |
| `command` | `//shade`, `//protect`, области, отмена | `ShadeCommand`, `Area`, `UndoHistory`, `WorldEditSelection` |
| `item` | валик | `BrushItem` |
| `compat` | WorldEdit и SecurityCraft, мягко | `WorldEditHook`, `WeMode`, `SecurityCraftCompat` |
| `net` | сеть | `Net`, `ShadeSyncPacket`, `BrushConfigPacket` |
| `client` | оттенки на клиенте, окно валика, контуры защиты | `ShadeClient`, `BrushScreen`, `ProtectionOverlay`, `ClientSetup` |
| `mixin`, `mixin.client` | поле чанка, защита, цвет граней | `LevelChunkMixin`; `FlTintMixinPlugin` (правка Embeddium — только если он стоит) |

## 4. Хочу добавить… — рецепты

| Что | Где | Образец | Не забыть |
|---|---|---|---|
| готовый оттенок в палитру валика | `ShadeColors` | существующие | оттенок — множитель `0xRRGGBB` |
| подкоманду `//shade` или `//protect` | `command/ShadeCommand` | `convert` | область — через `Area`; запись в `UndoHistory` |
| новый способ менять оттенок или защиту | только через `data/Shades` | `BrushItem` | `Shades` сам сохраняет чанк и рассылает видящим |
| от чего ещё защищать блок | `data/ProtectionEvents` или миксин | эндермен | проверить, что дроп не выпадает раньше отказа (`LevelMixin`) |
| поддержку ещё одного рендера чанков | миксин по образцу `BlockRendererEmbeddiumMixin`, подключение в `FlTintMixinPlugin` | — | `remap = false` для чужого класса |
| ещё один признак блока в секции | `data/ShadeSection` (формат) + `ShadeSyncPacket` | биты защиты | **формат секции — в сохранённых чанках мира:** только добавлять с чтением старого |

## 5. Данные

| Что | Где хранится | Ключ | Что при смерти | Как попадает клиенту |
|---|---|---|---|---|
| оттенки и защита блоков | чанк мира, поле из `LevelChunkMixin`, формат `ShadeSection` | секция по Y | — | `ShadeSyncPacket` при загрузке чанка и при изменении |
| оттенок и защита при копировании WorldEdit | NBT блока (ключи `fltint…`) | — | — | — |
| режим WorldEdit игрока | игрок, `getPersistentData` | `fltint_we_keep` | теряется (настройка сеанса) | — |
| настройки валика | NBT предмета | — | — | — |

Формат секции и ключи NBT не менять: ими записан живой мир.

## 6. Связи

- От модов семейства не зависит. RP Core — только отметка «модуль установлен», при `isLoaded("rpcore")`.
- WorldEdit, SecurityCraft — мягко (второй — отражением по его открытому API). Embeddium — мягко, через плагин миксинов.

## 7. Конфиги

`Config` (тип COMMON), 2 ключа.

## 8. Сеть

Канал `fltint:main`, протокол `1`: `ShadeSyncPacket` (сервер → клиент), `BrushConfigPacket` (клиент → сервер;
сервер проверяет, что это ведущий с валиком в руке, и границы значений).

## 9. Ловушки

- **Без capability нарочно:** обычное поле чанка быстрее в `setBlockState` и сохраняется вместе с чанком.
- **`destroyBlock` сначала роняет дроп, потом ставит воздух** — защиту надо проверять до дропа (`LevelMixin`).
- **Оттенок влияет на картинку только после пересборки секции чанка** на клиенте: `ShadeClient` её запрашивает.
- **Палитра секции — до 255 оттенков:** при переполнении новые оттенки в этой секции не встанут.
- Команды с двумя косыми (`//shade`) зарегистрированы и как `/shade`: WorldEdit перехватывает `//`.

## 10. Тесты

Игровых тестов нет. `python ../_family/family.py verify "FL Tint Any Block"` — компиляция. Проверка — в игре.

## 11. Документы в docs/

| Файл | Состояние |
|---|---|
| `MAP.md`, `INDEX.md` | действуют |
| `preview/` | снимки для описания мода |
