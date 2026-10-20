# UI specification (reverse engineered from the shipped app)

Generated with `ui_spec.py` (binary AXML layout decoder + resources.arsc reader).
Nothing here is copied source: these are *observations* of the shipped UI, used as a
build spec so we can reproduce the same UX with our own code and assets.

## Screen inventory (driver app)

| layout | elements | distinct view classes |
|---|---|---|
| `setting_fragment_layout` | 61 | FrameLayout, ImageView, LinearLayout, RelativeLayout, ScrollView, TextView |
| `order_fragment_main_layout` | 48 | FrameLayout, ImageView, LinearLayout, RelativeLayout, TextView, RecyclerView |
| `time_distance_fragment_layout` | 44 | LinearLayout, ScrollView, SeekBar, TextView, IconEditText |
| `permission_setting_fragment_layout` | 40 | FrameLayout, ImageView, LinearLayout, RelativeLayout, ScrollView, TextView |
| `alipay_fragment_layout` | 37 | FrameLayout, ImageView, LinearLayout, ProgressBar, RelativeLayout, ScrollView |
| `bottom_sheet_view` | 35 | LinearLayout, NumberPicker, RelativeLayout, TextView, FontTextView |
| `octopus_calculator_layout` | 33 | Button, ImageView, LinearLayout, ProgressBar, RelativeLayout, TextView |
| `option_order_option_row` | 33 | LinearLayout, TextView, GhostButton, PriceNumberPickerView |
| `wechat_fragment_layout` | 33 | FrameLayout, ImageView, LinearLayout, ProgressBar, RelativeLayout, ScrollView |
| `calculator_option_layout` | 31 | LinearLayout, RadioButton, RadioGroup, TextView, View, FontTextView |
| `driver_option_fragment_layout` | 31 | FrameLayout, ImageView, LinearLayout, RelativeLayout, ScrollView, TextView |
| `car_model_preview_item_v2_row` | 30 | ImageView, LinearLayout, RelativeLayout, TextView, View, GhostV2Button |
| `octopus_payment_dialog_layout` | 30 | Button, ImageView, LinearLayout, ProgressBar, RelativeLayout, TextView |
| `alipay_apply_request_fragment_layout` | 29 | FrameLayout, ImageView, LinearLayout, RelativeLayout, ScrollView, TextView |
| `font_size_fragment_layout` | 29 | FrameLayout, LinearLayout, RelativeLayout, ScrollView, SeekBar, TextView |
| `main_layout` | 28 | ImageView, LinearLayout, ProgressBar, RelativeLayout, TextView, Toolbar |
| `drop_order_order_row` | 26 | ImageView, LinearLayout, TextView, GhostButton |
| `payme_fragment_layout` | 25 | CheckBox, EditText, FrameLayout, ImageView, LinearLayout, ProgressBar |

## Design tokens (extracted values)

Spacing scale: `extra_small 4dp · small 8dp · medium 16dp · large 24dp`
Type scale: `small 20sp · medium 24sp · large 28sp · extra_large 32sp`
Controls: `button_height 48dp · swipe_button 78dp · swipe_padding 18dp`

Palette (reference values, re-declared under our own names in `Theme.kt`):

| reference name | value | our token |
|---|---|---|
| `order_gold` | `#BFFCA62A` | `Tokens.Gold` |
| `order_green` | `#BF73BA27` | `Tokens.GoldDim` |
| `black` | `#FF000000` | `Tokens.Panel` |
| `white` | `#FFFFFFFF` | `Tokens.White` |
| `slide_menu_background_color` | `#EE000000` | `Tokens.Sheet` |

## Rebuild status

| screen | reference layout | our implementation |
|---|---|---|
| Accept order | `accept_order_main_layout.xml` | `ui/AcceptOrderScreen.kt` (countdown, progress, sections, swipe bar) |
| Login | `login_fragment_layout.xml` | `ui/MainActivity.kt` → `LoginPane` |
| Driver home | `main_layout.xml` | `ui/MainActivity.kt` → `HomePane` |
| Active order | `order_fragment_main_layout.xml` | `ui/MainActivity.kt` → `OrderPane` (status-driven buttons) |

Full view trees: `research/ui/layouts/*.txt` (216 layouts, resource ids resolved to names).
Machine-readable: `research/ui/layout-index.json`, `research/ui/design-tokens.json`.

