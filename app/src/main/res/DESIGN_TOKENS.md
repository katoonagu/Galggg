# Design Tokens — Config Upload Card

Спецификация дизайна карточки "Загрузить конфиг" из Figma (node 22:13).

## 📐 Структура узлов

```
Content Container (22:13)
└── Загрузить конфиг (22:30)
    ├── Config Upload Background (22:31) - Фон карточки
    ├── config (22:32) - Иконка
    └── Config Upload Container (22:34)
        ├── Title (22:35) - Заголовок
        └── Description (22:36) - Описание
```

---

## 🎨 Цвета (colors.xml)

| Token Name | Value | Usage | Android Resource |
|------------|-------|-------|------------------|
| `background_primary` | `#181A1E` | Основной фон приложения | `@color/background_primary` |
| `config_card_background` | `#202128` | Фон карточки загрузки конфига | `@color/config_card_background` |
| `text_primary` | `#FFFFFF` | Основной цвет текста | `@color/text_primary` |
| `text_secondary` | `#B0B0B0` | Вторичный цвет текста | `@color/text_secondary` |
| `icon_background` | `#7386AF` | Фон иконки конфига | `@color/icon_background` |
| `button_background` | `#2D3035` | Фон кнопок | `@color/button_background` |

---

## 📏 Размеры (dimens.xml)

### Config Upload Card

| Token Name | Value | Description |
|------------|-------|-------------|
| `config_card_width` | `381dp` | Ширина карточки |
| `config_card_height` | `174dp` | Высота карточки |
| `config_card_corner_radius` | `34dp` | Радиус скругления углов карточки |
| `config_card_padding` | `24dp` | Внутренний отступ карточки |
| `config_card_margin_top` | `20dp` | Верхний внешний отступ |

### Config Icon

| Token Name | Value | Description |
|------------|-------|-------------|
| `config_icon_width` | `41dp` | Ширина иконки |
| `config_icon_height` | `37dp` | Высота иконки |
| `config_icon_margin_end` | `16dp` | Отступ справа от иконки |
| `config_icon_background_radius` | `12dp` | Радиус скругления фона иконки |
| `config_icon_padding` | `4dp` | Внутренний отступ иконки |

### Text Dimensions

| Token Name | Value | Description |
|------------|-------|-------------|
| `config_title_text_size` | `32sp` | Размер шрифта заголовка |
| `config_description_text_size` | `15sp` | Размер шрифта описания |
| `config_description_margin_top` | `8dp` | Отступ сверху от описания |

### General Spacing

| Token Name | Value | Usage |
|------------|-------|-------|
| `spacing_small` | `8dp` | Малые отступы |
| `spacing_medium` | `16dp` | Средние отступы |
| `spacing_large` | `24dp` | Большие отступы |

### Home Screen Text

| Token Name | Value | Description |
|------------|-------|-------------|
| `home_title_text_size` | `24sp` | Размер заголовка Home |
| `home_status_text_size` | `18sp` | Размер текста статуса |
| `home_button_text_size` | `16sp` | Размер текста кнопки |

---

## 🔤 Типографика

### Семейство шрифтов

**Garet** — основной шрифт приложения

| Начертание | Файл | Вес | Android Resource |
|------------|------|-----|------------------|
| Book | `garet_book.ttf` | 300 | `@font/garet_book` |
| Heavy | `garet_heavy.ttf` | 700 | `@font/garet_heavy` |

**Family XML**: `@font/garet`

### Стили текста

#### Config Upload Title
```xml
android:fontFamily="@font/garet"
android:textColor="@color/text_primary"
android:textSize="@dimen/config_title_text_size"
android:textFontWeight="300"
android:textStyle="normal"
```

#### Config Upload Description
```xml
android:fontFamily="@font/garet"
android:textColor="@color/text_primary"
android:textSize="@dimen/config_description_text_size"
android:textFontWeight="300"
android:textStyle="normal"
```

---

## 🖼️ Ресурсы изображений

### Config Icon
- **Исходный файл**: `65621e07b540ffa12182bebaae499ad9fa967be1.svg`
- **Расположение**: `app/src/main/res/drawable/`
- **Формат**: SVG
- **Использование**: `@drawable/config`
- **Размеры**: 41x37 dp
- **Content Description**: `@string/config_upload_icon_content_desc`

---

## 📱 Android Layout Implementation

### Карточка Config Upload

```xml
<FrameLayout
    android:id="@+id/btnConfigUpload"
    android:layout_width="@dimen/config_card_width"
    android:layout_height="@dimen/config_card_height"
    android:background="@drawable/config_uplo"
    android:clickable="true"
    android:focusable="true">
    
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="@dimen/config_card_padding">
        
        <!-- Icon -->
        <ImageView
            android:id="@+id/imageConfigIcon"
            android:layout_width="@dimen/config_icon_width"
            android:layout_height="@dimen/config_icon_height"
            android:background="@drawable/bg_config_icon"
            android:src="@drawable/config" />
        
        <!-- Title -->
        <TextView
            android:id="@+id/configUploadTitle"
            android:text="@string/config_upload_title"
            android:textColor="@color/text_primary"
            android:textSize="@dimen/config_title_text_size"
            android:fontFamily="@font/garet" />
        
        <!-- Description -->
        <TextView
            android:id="@+id/configUploadDescription"
            android:text="@string/config_upload_description"
            android:textColor="@color/text_primary"
            android:textSize="@dimen/config_description_text_size"
            android:fontFamily="@font/garet" />
            
    </androidx.constraintlayout.widget.ConstraintLayout>
</FrameLayout>
```

---

## ✅ Реализованные файлы

### Созданные ресурсы:
- ✅ `app/src/main/res/values/colors.xml` — цветовая палитра
- ✅ `app/src/main/res/values/dimens.xml` — размеры и отступы
- ✅ `app/src/main/res/drawable/config_uplo.xml` — фон карточки (обновлён)
- ✅ `app/src/main/res/drawable/bg_config_icon.xml` — фон иконки (обновлён)
- ✅ `app/src/main/res/layout/activity_main.xml` — главный layout (обновлён)

### Существующие ресурсы:
- ✅ `app/src/main/res/values/strings.xml` — текстовые строки
- ✅ `app/src/main/res/font/garet.xml` — семейство шрифтов
- ✅ `app/src/main/res/drawable/config.png` — иконка конфига

---

## 📊 Сравнение с предыдущей версией

| Параметр | Было | Figma | Применено |
|----------|------|-------|-----------|
| Ширина карточки | 399dp | 381dp | ✅ 381dp |
| Высота карточки | 162dp | 174dp | ✅ 174dp |
| Corner Radius | 43dp | 34dp | ✅ 34dp |
| Фон карточки | `#202128` | `#202128` | ✅ Без изменений |
| Размер заголовка | 32sp | 32sp | ✅ Без изменений |
| Размер описания | 15sp | 15sp | ✅ Без изменений |

---

## 🎯 Выводы

Все значения из дизайна Figma успешно перенесены в Android XML ресурсы с сохранением:
- ✅ Точных размеров (px → dp)
- ✅ Цветов (HEX → Android color)
- ✅ Типографики (Garet Book, размеры sp)
- ✅ Структуры иерархии узлов
- ✅ Скругления углов и отступов

Layout теперь полностью соответствует дизайну из Figma.

