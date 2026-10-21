# Hosil Shared Design System Specification

**Document Version:** 1.0  
**Date:** 14 October 2026  
**Status:** Approved Specification  
**Applies to:** Hosil Bozor, Hosil Farm, Hosil Logistika (Flutter 3.x), Hosil Admin (Spring Boot + Thymeleaf/HTMX)

---

## 1. Overview and Design Principles

The Hosil Ecosystem serves farmers, commercial agricultural suppliers, wholesale buyers, and logistics carriers across Uzbekistan. Field conditions range from direct sunlight in open fields to low-bandwidth 2G/3G connectivity in rural districts. The design system prioritizes legibility, speed, offline clarity, and operational trust.

```
+-------------------------------------------------------------------------------+
|                           HOSIL SHARED DESIGN SYSTEM                          |
+------------------------------------+------------------------------------------+
|  Mobile Apps (Flutter 3.x)         |  Web Back-Office (Thymeleaf + HTMX)      |
|  - Hosil Bozor (Marketplace)       |  - Seller & Carrier Verification         |
|  - Hosil Farm (Offline Farm Mgmt)  |  - Listing & Review Moderation           |
|  - Hosil Logistika (Freight)       |  - Audit Log & Reference Data Mgmt       |
+------------------------------------+------------------------------------------+
|  Shared Tokens: Colors | Typography | Spacing | Elevation | Status Indicators  |
+-------------------------------------------------------------------------------+
```

### 1.1 Core Principles

1. **Outdoor Legibility First:** Farmers use Hosil Farm and Hosil Logistika outdoors under direct, harsh sunlight. Low-contrast grey-on-grey interfaces are prohibited. Core text and interactive controls adhere to WCAG AAA contrast (minimum 7:1 for text, 4.5:1 for borders and icons).
2. **Rugged Touch Targets:** Touch targets must be at least **48x48 dp** with a minimum 8 dp touch separation. Farmers often operate devices one-handed or while wearing work gloves.
3. **Unambiguous Offline States:** The interface must never leave the user wondering if data was saved or sent. Offline mode is a first-class state, indicated by persistent, non-intrusive status pills and queue counts.
4. **Decoupled Operational Trust:** Visual components for ratings strictly distinguish personal interpersonal conduct from physical produce quality (per `DEC-018` and `docs/12_reputation_and_reviews.md`).
5. **Cultural and Linguistic Precision:** Full typographic support for Uzbek Latin (`Oʻ`, `Gʻ`, `oʻ`, `gʻ`), Russian Cyrillic, and standardized currency formatting (UZS with non-breaking thousand separators, e.g., `8 500 UZS`).

---

## 2. Color Tokens

### 2.1 Brand & Semantic Palettes

| Token Name | Hex Code | Role & Usage | Contrast vs White |
| :--- | :--- | :--- | :--- |
| `hosil-green-800` | `#1B5E20` | Dark Green: Primary headers, high-contrast text | 9.5:1 (AAA) |
| `hosil-green-600` | `#2E7D32` | Hosil Brand Green: Primary buttons, active tabs | 5.2:1 (AA Large) |
| `hosil-green-50` | `#E8F5E9` | Light Green: Success surfaces, approved badges | 1.1:1 (Surface) |
| `hosil-blue-700` | `#0369A1` | Logistics Navy: Logistika headers, freight tags | 7.4:1 (AAA) |
| `hosil-blue-600` | `#0284C7` | Hosil Blue: Carrier quotes, logistics CTAs | 4.6:1 (AA) |
| `hosil-blue-50` | `#E0F2FE` | Ice Blue: Logistics backgrounds, active shipments | 1.1:1 (Surface) |
| `hosil-amber-700` | `#B45309` | Harvest Bronze: Warning banners, queue alerts | 5.8:1 (AA Large) |
| `hosil-amber-600` | `#D97706` | Harvest Amber: Pending sync, offline status | 3.3:1 (Badge BG) |
| `hosil-amber-50` | `#FEF3C7` | Warm Amber: Warning surfaces, pending tags | 1.1:1 (Surface) |
| `hosil-red-700` | `#B91C1C` | Alert Dark Red: Destructive errors, dispute tags | 7.3:1 (AAA) |
| `hosil-red-600` | `#DC2626` | Crimson Red: Error states, reject buttons | 4.8:1 (AA) |
| `hosil-red-50` | `#FEE2E2` | Light Red: Error alerts, flagged listing surfaces| 1.1:1 (Surface) |

### 2.2 Neutral & Surface Scale (Slate)

| Token Name | Hex Code | Application |
| :--- | :--- | :--- |
| `slate-900` | `#0F172A` | Primary typography, admin top bar, high-contrast borders |
| `slate-800` | `#1E293B` | Secondary headers, drawer headers, admin sidebar |
| `slate-700` | `#334155` | Secondary text, active tab indicators, table headers |
| `slate-600` | `#475569` | Field labels, descriptive subheaders |
| `slate-500` | `#64748B` | Placeholder text, inactive tab labels, timestamps |
| `slate-400` | `#94A3B8` | Subtle borders, disabled icon states |
| `slate-300` | `#CBD5E1` | Standard card and input container borders |
| `slate-200` | `#E2E8F0` | Dividers, subtle borders, table grid lines |
| `slate-100` | `#F1F5F9` | Secondary background, table alternate row fill |
| `slate-50` | `#F8FAFC` | Main application canvas background |
| `white` | `#FFFFFF` | Card surface, modal surface, input background |

### 2.3 Outdoor High-Contrast Mode Tokens

When high-contrast mode is active (or detected via ambient light sensors exceeding 25,000 lux):
- Background switches to pure white (`#FFFFFF`) or high-contrast deep black (`#000000` if dark mode).
- Card borders intensify to `2px solid #0F172A`.
- Muted grey text (`#64748B`) automatically promotes to `#1E293B` (minimum 9:1 contrast).
- Shadow elevations are replaced with crisp solid borders.

---

## 3. Typography System

### 3.1 Type Scale

The system standardizes on the **Inter** font family (with fallback to system font: Roboto on Android, SF Pro on iOS). All monetary and quantitative displays must use **tabular numeric figures** (`tabular-nums`) to prevent layout shifts.

| Style Role | Font Size (sp/px) | Line Height | Weight | Letter Spacing | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `displayLarge` | 28 sp | 34 sp | 800 (Bold) | -0.5 sp | Dashboard hero headers, harvest totals |
| `headlineMedium` | 22 sp | 28 sp | 700 (Bold) | -0.2 sp | Screen titles, modal headers |
| `titleLarge` | 18 sp | 24 sp | 700 (Bold) | 0.0 sp | Product title, section titles |
| `titleMedium` | 16 sp | 22 sp | 600 (SemiBold)| 0.1 sp | Card headers, table column headers |
| `bodyLarge` | 16 sp | 24 sp | 400 (Regular) | 0.2 sp | Primary reading copy, input values |
| `bodyMedium` | 14 sp | 20 sp | 400 (Regular) | 0.2 sp | Secondary descriptions, list item details |
| `labelLarge` | 14 sp | 20 sp | 700 (Bold) | 0.1 sp | Button text, action links, tab titles |
| `labelMedium` | 12 sp | 16 sp | 600 (SemiBold)| 0.2 sp | Status badges, category pills |
| `labelSmall` | 10 sp | 14 sp | 700 (Bold) | 0.4 sp | Micro badges, offline counters, metadata |

### 3.2 Numeric & Currency Formatting Guidelines

- Currency: Always formatted as `<integer> UZS` with non-breaking spaces as thousand separators (e.g., `12 500 000 UZS`). No cents/tiyin are shown in general listings.
- Mass & Volume: Formatted with standard metric units from the catalog (`kg`, `t`, `l`, `qop`, `dona`). Example: `15 000 kg` or `15 t`.
- Dates: Displayed in standard ISO or locale format: `DD.MM.YYYY` (e.g., `14.10.2026`).

---

## 4. Spacing and Grid System

### 4.1 Base Unit: 8dp (with 4dp micro-increments)

```
spacing-1 : 4 dp   (Micro padding, icon offsets)
spacing-2 : 8 dp   (Tight spacing, badge padding, grid gutter)
spacing-3 : 12 dp  (Card interior separation)
spacing-4 : 16 dp  (Standard screen edge margin, form gap)
spacing-6 : 24 dp  (Section separation)
spacing-8 : 32 dp  (Major flow transitions, dialog margins)
spacing-12: 48 dp  (Standard touch target size, bottom bar height 56-64dp)
```

### 4.2 Touch Target Compliance

Every interactive element (buttons, chips, checkboxes, steppers, radio rows) must have an active hit-test box of **at least 48x48 dp**. If visual styling requires a smaller element (such as a 20x20 dp checkbox), the widget must wrap in a container with minimum 48x48 dp padding.

---

## 5. Standard Component Library

### 5.1 Buttons

1. **Primary Action Button:**
   - Background: `hosil-green-600` (`#2E7D32`), Text: White (`#FFFFFF`), Height: `48 dp` (Mobile) / `40 dp` (Admin), Corner Radius: `8 dp`.
   - Disabled state: Background `#CBD5E1`, Text `#94A3B8`.
2. **Secondary Outlined Button:**
   - Border: `1.5px solid #CBD5E1`, Background: White (`#FFFFFF`), Text: `#1E293B`, Height: `48 dp`.
3. **Destructive Button:**
   - Background: `hosil-red-600` (`#DC2626`), Text: White (`#FFFFFF`), Height: `48 dp`. Used for cancellations, listing rejections, farm deletions.
4. **Logistics Primary Button:**
   - Background: `hosil-blue-600` (`#0284C7`), Text: White (`#FFFFFF`), used in Hosil Logistika for quotes and load bookings.

### 5.2 Input Controls & Quantity Steppers

- **Text & Numeric Inputs:** White background, `1.5px solid #CBD5E1` border, `8 dp` corner radius. Focus border: `2px solid #2E7D32`.
- **Integrated Unit Stepper:** For harvest ordering and field activities:
  ```
  +-------------------------------------------------------------+
  |  Buyurtma miqdori (Order Quantity)                         |
  |  +-------+-----------------------------+-----------------+  |
  |  |  [-]  |           5 000             | [ kg ▾ ]  [+]   |  |
  |  +-------+-----------------------------+-----------------+  |
  |  Minimal buyurtma (MOQ): 500 kg | Qadam: 100 kg             |
  +-------------------------------------------------------------+
  ```
- **Error State:** Border `#DC2626`, error message in `labelMedium` `#B91C1C` with `⚠️` icon.

### 5.3 Badges & Chips

| Badge Type | Background | Border | Text | Example Label |
| :--- | :--- | :--- | :--- | :--- |
| `Verified Farmer` | `#EBF7ED` | `#C2E6C6` | `#2E7D32` | `✓ Fermer Tasdiqlangan` |
| `Commercial Seller`| `#EFF6FF` | `#BFDBFE` | `#1D4ED8` | `✓ Rasmiy Taʼminotchi` |
| `Carrier Verified` | `#E0F2FE` | `#BAE6FD` | `#0284C7` | `✓ Tekshirilgan Haydovchi`|
| `Pending Review` | `#FEF3C7` | `#FDE68A` | `#B45309` | `⏳ Tekshiruvda` |
| `Flagged / Risk` | `#FEE2E2` | `#FCA5A5` | `#B91C1C` | `⚠️ Xavfli eʼlon` |
| `Double-Blind Tag` | `#F1F5F9` | `#CBD5E1` | `#475569` | `🔒 Ikki tomonlama yopiq` |

---

## 6. Offline Synchronization Indicators

Field operations in Hosil Farm and Logistika must reflect connectivity status unambiguously without modal interruptions.

### 6.1 Status Hierarchy

```
+-------------------------------------------------------------------------------+
|  SYNC STATE           | VISUAL PILL                     | BEHAVIOR            |
+-----------------------+---------------------------------+---------------------+
|  1. Online & Synced   | [ ● Sinxronlangan ] (Green)     | Collapses after 3s  |
|  2. Queued Mutations  | [ ● Oflayn (3 ta navbatda) ]     | Persistent pill     |
|  3. Active Syncing    | [ 🔄 Sinxronlanmoqda... ]       | Rotating icon       |
|  4. Sync Conflict     | [ ⚠️ Ziddiyat (1 ta koʻrish) ]   | Tappable banner     |
|  5. Hard Connection   | [ ⚡ Server bilan aloqa yoʻq ]   | Retries in backoff  |
+-------------------------------------------------------------------------------+
```

### 6.2 Visual Design

- **Queued / Offline Pill:** Amber background (`#FEF3C7`), border (`#FDE68A`), text (`#B45309`), pulsing amber dot. Tapping opens the Sync Queue screen (`docs/design/03_hosil_farm_ui.md#screen-16`).
- **Conflict Warning Banner:** Amber-red gradient bar pinned beneath app bar with direct action: `[ Ziddiyatni hal qilish ]`.

---

## 7. Decoupled Two-Sided Review Components (DEC-018)

Per `docs/12_reputation_and_reviews.md`, review components **must strictly decouple** personal seller/buyer conduct from physical produce quality. Single composite star ratings that blend conduct and product are prohibited.

```
+-------------------------------------------------------------------------------+
|                      IKKI TOMONLAMA BAHOLASH (REPUTATION)                     |
+-------------------------------------------------------------------------------+
|  14 kunlik yopiq baholash davri: Ikkala tomon baholamaguncha sharh ochilmaydi |
+-------------------------------------------------------------------------------+
|  [A] SOTUVCHI MUOMALASI VA HALOLLIGI (Seller Conduct & Integrity)            |
|      Kelishuvga sodiqlik, vaqtida aloqaga chiqish va xushmuomalalik:          |
|      ⭐  ⭐  ⭐  ⭐  ☆   (4.0 / 5.0)                                          |
+-------------------------------------------------------------------------------+
|  [B] MAHSULOTNING AMALDAGI SIFATI (Physical Produce Quality)                  |
|      Hosilning saralanganligi, chirik/nuqson yoʻqligi, eʼlonga mosligi:       |
|      ⭐  ⭐  ⭐  ⭐  ⭐   (5.0 / 5.0)                                          |
+-------------------------------------------------------------------------------+
|  Teng vaznli umumiy indeks: Conduct (50%) + Produce Quality (50%)             |
+-------------------------------------------------------------------------------+
```

---

## 8. Flutter Implementation Guide

Mobile apps consume tokens through typed extensions on `ThemeData`:

```dart
// lib/core/theme/hosil_colors.dart
import 'package:flutter/material.dart';

class HosilColors extends ThemeExtension<HosilColors> {
  final Color greenPrimary;
  final Color greenSurface;
  final Color blueLogistics;
  final Color amberWarning;
  final Color redAlert;
  final Color neutralBackground;

  const HosilColors({
    required this.greenPrimary,
    required this.greenSurface,
    required this.blueLogistics,
    required this.amberWarning,
    required this.redAlert,
    required this.neutralBackground,
  });

  static const light = HosilColors(
    greenPrimary: Color(0xFF2E7D32),
    greenSurface: Color(0xFFE8F5E9),
    blueLogistics: Color(0xFF0284C7),
    amberWarning: Color(0xFFD97706),
    redAlert: Color(0xFFDC2626),
    neutralBackground: Color(0xFFF8FAFC),
  );

  @override
  HosilColors copyWith({...}) => ...;

  @override
  HosilColors lerp(ThemeExtension<HosilColors>? other, double t) => ...;
}
```
