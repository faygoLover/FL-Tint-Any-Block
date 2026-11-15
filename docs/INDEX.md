# FL Tint Any Block — указатель кода

Создано `python _family/tools/mapgen.py` 15.11.2026, версия 1.0.1-1.20.1-forge, коммит `ed50f35`.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 30 файлов, 2629 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.fltint` | 4 | 224 |
| `faygolover.fltint.client` | 4 | 496 |
| `faygolover.fltint.command` | 4 | 490 |
| `faygolover.fltint.compat` | 3 | 256 |
| `faygolover.fltint.data` | 5 | 593 |
| `faygolover.fltint.item` | 1 | 219 |
| `faygolover.fltint.mixin` | 4 | 138 |
| `faygolover.fltint.mixin.client` | 2 | 64 |
| `faygolover.fltint.net` | 3 | 149 |

## 2. Классы

### `faygolover.fltint`
- `Config`, 21 — *без описания*
- `FlTint`, 68 — *без описания*
- `ShadeColors`, 98 — Оттенок = множитель цвета 0xRRGGBB: каждый канал текстуры умножается на канал/255.
- `Structural`, 37 — Фильтр solid: «конструкция, а не мебель» — то, что ограничивает мир и движение.

### `faygolover.fltint.client`
- `BrushScreen`, 185 — Окно валика: готовые оттенки, свой hex, ластик, режим, радиус, «только такой же блок».
- `ClientSetup`, 104 — *без описания*
- `ProtectionOverlay`, 100 — ГМ с валиком в руке видит контуры защищённых блоков в радиусе 12 (только открытые — те, что не закрыты со всех сторон).
- `ShadeClient`, 107 — Оттенки и защита на клиенте: SectionPos.asLong → неизменяемая секция.

### `faygolover.fltint.command`
- `Area` (interface), 33 — Набор позиций для /shade: коробка по двум углам или выделение WorldEdit.
- `ShadeCommand`, 300 — //shade (и /shade) — оттенки: set &lt;цвет&gt; [solid|блок], clear [solid|блок] — по выделению WorldEdit; convert [цвет] — укреплённые блоки SecurityCraft → ванильные + тот же оттенок + защита, без обновления соседей; region &lt;о…
- `UndoHistory`, 102 — Последние покраски и правки защиты игрока (валик, //shade set|clear, //protect set|clear) для undo.
- `WorldEditSelection`, 55 — Выделение WorldEdit игрока.

### `faygolover.fltint.compat`
- `SecurityCraftCompat`, 94 — SecurityCraft без зависимости при сборке: всё через рефлексию по его открытому API.
- `WeMode`, 33 — Что делает WorldEdit с оттенком и защитой, когда ставит блок без них (//set, //replace, вставка неокрашенного): replace (по умолчанию) — снимает; keep — оставляет как было.
- `WorldEditHook`, 129 — WorldEdit: оттенок и защита едут вместе с блоком — как содержимое сундука, в NBT блока (ключи fltint:*).

### `faygolover.fltint.data`
- `ProtectionEvents`, 89 — Защищённые блоки: ломает только ГМ (оператор 2+).
- `ShadeChunk` (interface), 9 — Добавлено к LevelChunk миксином: оттенки чанка (null, пока ничего не покрашено).
- `ShadeData`, 99 — Оттенки одного чанка на сервере: секция по Y → ShadeSection.
- `ShadeSection`, 194 — Данные одной секции 16×16×16: - оттенки: палитра до 255 цветов + индекс на блок (0 = без оттенка), 4 КБ; - защита: бит на блок (512 байт), только если в секции есть защищённые блоки.
- `Shades`, 202 — Серверное API оттенков и защиты + сохранение в чанк + рассылка изменений игрокам, которые видят чанк.

### `faygolover.fltint.item`
- `BrushItem`, 219 — Валик оттенка (только ГМ = оператор 2+ уровня).

### `faygolover.fltint.mixin`
- `EndermanTakeBlockGoalMixin`, 20 — Эндермен берёт блок в руки, не проверяя, удалось ли его убрать, — защищённый блок он бы «скопировал».
- `FlTintMixinPlugin`, 34 — MixinExtras + правка Embeddium только при Embeddium в сборке.
- `LevelChunkMixin`, 59 — Оттенки и защита чанка — обычное поле (без capability: быстрее в setBlockState, сохраняется через ChunkDataEvent).
- `LevelMixin`, 25 — destroyBlock сначала роняет дроп, потом ставит воздух: защита в LevelChunk не дала бы сменить блок, но дроп уже выпал бы (дюп).

### `faygolover.fltint.mixin.client`
- `BlockRendererEmbeddiumMixin`, 31 — Embeddium: цвета вершин квада (ABGR) считаются здесь для каждого квада блока, тинт или нет.
- `ModelBlockRendererMixin`, 33 — Ванильная сборка чанков (без Embeddium): тот же множитель на цвет квада.

### `faygolover.fltint.net`
- `BrushConfigPacket` (record), 37 — Окно валика → сервер: новые цвет, режим, радиус, «только такой же блок».
- `Net`, 30 — *без описания*
- `ShadeSyncPacket`, 82 — Оттенки чанка сервер → клиент.

Классов без описания: 4 из 30.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `BlockEvent.BreakEvent` | `ProtectionEvents.onBreak`, `ProtectionEvents.onGmBreak` |
| `BuildCreativeModeTabContentsEvent` | `FlTint.creativeTab` |
| `ChunkDataEvent.Load` | `Shades.onChunkLoad` |
| `ChunkDataEvent.Save` | `Shades.onChunkSave` |
| `ChunkEvent.Unload` | `ClientSetup.chunkUnload` |
| `ChunkWatchEvent.Watch` | `Shades.onChunkWatch` |
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientSetup.loggingOut` |
| `CustomizeGuiOverlayEvent.DebugText` | `ClientSetup.debugText` |
| `EditSessionEvent` | `WorldEditHook.onEditSession` |
| `ExplosionEvent.Detonate` | `ProtectionEvents.onExplosion` |
| `InputEvent.InteractionKeyMappingTriggered` | `ClientSetup.pickBlock` |
| `LevelEvent.Unload` | `ClientSetup.levelUnload` |
| `LivingDestroyBlockEvent` | `ProtectionEvents.onMobDestroy` |
| `PistonEvent.Pre` | `ProtectionEvents.onPiston` |
| `PlayerEvent.BreakSpeed` | `ProtectionEvents.onBreakSpeed` |
| `PlayerEvent.PlayerLoggedOutEvent` | `UndoHistory.onLogout` |
| `PlayerInteractEvent.LeftClickBlock` | `ProtectionEvents.onLeftClick` |
| `RegisterColorHandlersEvent.Item` | `ClientSetup.itemColors` |
| `RegisterCommandsEvent` | `ShadeCommand.register` |
| `RenderLevelStageEvent` | `ProtectionOverlay.render` |
| `ServerAboutToStartEvent` | `FlTint.serverStarting` |
| `TickEvent.ClientTickEvent` | `ProtectionOverlay.clientTick` |
| `TickEvent.ServerTickEvent` | `Shades.onServerTick` |

## 4. Сеть

Канал: `faygolover/fltint/net/Net.java`, имя `main`, версия протокола `1`.
- `ShadeSyncPacket` (направление не указано в регистрации)
- `BrushConfigPacket` (направление не указано в регистрации)

Классы с обработчиками пакетов (2): `BrushConfigPacket`, `ShadeSyncPacket`.

## 5. Команды

- `faygolover/fltint/command/ShadeCommand.java`: `/shade` `region` `get` `undo` `colors` `we` `keep` `replace` `shade` `/protect` `solid` `set` `clear` `convert`

## 6. Данные

- getPersistentData: `WeMode` ×2, теги: `fltint_we_keep`

## 7. Конфиги

- common: `Config`, вызовов define 2

## 8. Что берёт из RP Core и что ему даёт

Не использует.

## 9. Связи с другими модами

Проверки `isLoaded`: `rpcore` (FlTint); `securitycraft` (SecurityCraftCompat); `worldedit` (FlTint, ShadeCommand).

Отражение (`Class.forName`):
- `SecurityCraftCompat` → `net.geforcemods.securitycraft.api.IReinforcedBlock`
- `SecurityCraftCompat` → `net.geforcemods.securitycraft.util.Reinforced`
- `SecurityCraftCompat` → `net.geforcemods.securitycraft.SCContent`

Миксины (5):
- `EndermanTakeBlockGoalMixin` (mixins) → targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal"
- `LevelChunkMixin` (mixins) → LevelChunk.class
- `LevelMixin` (mixins) → Level.class
- `client.BlockRendererEmbeddiumMixin` (client) → BlockRenderer.class, remap = false
- `client.ModelBlockRendererMixin` (client) → ModelBlockRenderer.class

## 10. Зарегистрированные id

- `faygolover/fltint/FlTint.java` (1): `brush`
