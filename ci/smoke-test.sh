#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK and checks that you can log an expense
# and an income, set up a repeating one, set a budget and see what's left, read the reports,
# delete an entry, keep everything after the app is closed, add an account in another currency
# that is kept as its own group, move money between currencies, and use the launcher shortcut.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.selavu
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  grep -E "AndroidRuntime|FATAL|Selavu" "$OUT/logcat.txt" | tail -40 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3 4 5 6; do
    rm -f "$OUT/$1.xml"
    adb shell rm -f /sdcard/ui.xml
    local said
    said=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 || true)
    if ! echo "$said" | grep -q "dumped to" || ! adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 \
      || ! grep -q '<hierarchy' "$OUT/$1.xml"; then
      echo "(couldn't read the screen: ${said//$'\n'/ }; trying again)"
      rm -f "$OUT/$1.xml"
      sleep 1
      continue
    fi
    # The emulator's own apps sometimes freeze while it warms up; wave the popup away.
    if grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml"; then
      echo "(dismissing a system 'isn't responding' popup)"
      python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
      sleep 3
      continue
    fi
    return 0
  done
  [ -f "$OUT/$1.xml" ] || fail "couldn't read what's on screen ($1)"
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
has() { grep -qF "$2" "$OUT/$1.xml"; }
# Scrolls down until <text> is on screen (re-reading the screen into <dump name>), for tall forms.
scroll_to() { # scroll_to <dump name> <text>
  local i
  for i in 1 2 3 4; do
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" > /dev/null 2>&1 && return 0
    adb shell input swipe 540 1700 540 700 300
    sleep 1
    dump "$1"
  done
}
expect() { has "$1" "$2" || fail "$3"; }
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Launching the app"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
dump home
shot 1-empty
expect home 'text="Balance"' "the home screen didn't open"
expect home 'text="Nothing logged yet"' "a fresh install doesn't say nothing is logged yet"
echo "PASS: opens on the home screen"

echo "--- Adding an expense"
tap home "Expense"
sleep 2
dump add1
expect add1 'text="New expense"' "the Expense button didn't open a new expense"
adb shell input text 250
hide_keyboard
dump add1
tap add1 "Groceries"
sleep 1
dump add1
shot 2-new-expense
tap add1 "Save"
sleep 2
dump home
expect home 'text="−₹250"' "the new expense isn't in the list"
expect home 'text="Groceries"' "the expense's category isn't shown"
echo "PASS: logs an expense"

echo "--- Adding an income"
tap home "Income" last # the button, below the "Income" total
sleep 2
dump add2
expect add2 'text="New income"' "the Income button didn't open a new income"
adb shell input text 50000
hide_keyboard
dump add2
tap add2 "Save"
sleep 2
dump home
expect home 'text="+₹50,000"' "the income isn't in the list"
expect home 'text="₹49,750"' "the balance isn't income minus spending"
echo "PASS: logs an income and the balance adds up"

echo "--- A repeating expense"
tap home "Expense"
sleep 2
adb shell input text 999
hide_keyboard
dump add3
tap add3 "Entertainment"
tap add3 "Note (optional)"
sleep 1
adb shell input text Netflix
hide_keyboard
dump add3
tap add3 "Every month"
sleep 1
dump add3
expect add3 "logged every month on its own" "choosing Every month doesn't say it will repeat"
shot 3-repeating
tap add3 "Save"
sleep 2
dump home
expect home 'content-desc="Repeats"' "the repeating expense isn't marked as repeating"
expect home 'text="Netflix"' "the note isn't shown"
echo "PASS: sets up a repeating expense"

echo "--- Budget"
tap home "Settings"
sleep 2
dump settings
tap settings "Spending budget"
sleep 1
dump budget
tap budget "Budget amount"
sleep 1
adb shell input text 20000
hide_keyboard
dump budget
tap budget "Save" last
sleep 1
dump settings
expect settings "a month" "the budget wasn't saved"
expect settings "1 active" "the repeating entry isn't listed in Settings"
tap settings "Home"
sleep 2
dump home
shot 4-home-with-budget
expect home 'text="₹18,751 left"' "the budget card doesn't show what's left"
expect home "a day for the next" "the budget card doesn't say how much can be spent a day"
echo "PASS: budget shows what's left"

echo "--- Reports"
tap home "Reports"
sleep 2
dump reports
shot 5-reports
expect reports 'text="BY CATEGORY"' "Reports didn't open"
expect reports 'text="₹1,249"' "Reports doesn't total the spending"
expect reports 'text="Entertainment"' "Reports doesn't list the categories"
expect reports 'text="80%"' "Reports doesn't show each category's share"
tap reports "Entertainment"
sleep 2
dump detail
expect detail 'text="1 ENTRY"' "a category's page doesn't list its entries"
adb shell input keyevent KEYCODE_BACK
sleep 1
for p in Week Year Day; do
  dump reports
  tap reports "$p"
  sleep 1
done
dump reports
shot 6-reports-day
echo "PASS: reports by category and period"

echo "--- Deleting an entry"
dump reports
tap reports "Home"
sleep 2
dump home
tap home "Groceries"
sleep 2
dump edit
expect edit 'text="Edit expense"' "tapping an entry didn't open it"
scroll_to edit "Delete"
tap edit "Delete"
sleep 1
dump confirm
tap confirm "Delete" last
sleep 2
dump home
has home 'text="−₹250"' && fail "the deleted expense is still listed"
echo "PASS: deletes an entry"

echo "--- Still there after closing the app"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
dump home
expect home 'text="+₹50,000"' "entries were lost when the app was closed"
expect home 'text="Netflix"' "the repeating entry was lost when the app was closed"
echo "PASS: keeps everything"

echo "--- Accounts and a second currency"
dump home
tap home "Settings"
sleep 2
dump settings
tap settings "Accounts"
sleep 2
dump accounts
expect accounts 'text="Cash"' "the Accounts screen doesn't list the Cash account"
tap accounts "New account"
sleep 2
dump newacc
tap newacc "Name" last
sleep 1
adb shell input text Chase
hide_keyboard
dump newacc
tap newacc '$'
sleep 1
dump newacc
tap newacc "Save" last
sleep 2
dump accounts
shot 8-accounts
expect accounts 'text="Chase"' "the new account isn't listed"
adb shell input keyevent KEYCODE_BACK
sleep 2
dump settings
tap settings "Home"
sleep 2
dump home
expect home 'text="$"' "Home has no currency switcher after adding a dollar account"
echo "PASS: adds an account in another currency"

echo "--- Income in dollars stays in its own group"
tap home "Income" last
sleep 2
adb shell input text 1000
hide_keyboard
dump inc
tap inc "Chase"
sleep 1
dump inc
tap inc "Save"
sleep 2
dump home
has home 'text="+$1,000"' && fail "dollar income shows in the rupee group"
tap home '$'
sleep 2
dump home
shot 9-dollars
expect home 'text="+$1,000"' "the dollar income isn't in the dollar group"
expect home 'text="$1,000"' "the dollar balance isn't shown"
echo "PASS: each currency is its own group"

echo "--- Transfer from dollars to rupees"
tap home "Expense"
sleep 2
adb shell input text 500
hide_keyboard
dump tr
tap tr "Transfer"
sleep 1
dump tr
tap tr "Chase" first
sleep 1
dump tr
tap tr "Cash" last
sleep 1
dump tr
scroll_to tr "Amount received"
expect tr 'text="Amount received"' "a transfer between currencies doesn't ask what arrived"
tap tr "Amount received"
sleep 1
adb shell input text 42000
hide_keyboard
dump tr
shot 10-transfer
tap tr "Save"
sleep 2
dump home
expect home 'text="−$500"' "the transfer isn't shown going out of the dollar group"
expect home 'text="+$1,000"' "the transfer changed the dollar income"
tap home "₹"
sleep 2
dump home
shot 11-rupees-after-transfer
expect home 'text="+₹42,000"' "the transfer isn't shown arriving in the rupee group"
echo "PASS: transfers between currencies"

echo "--- A credit card and its bill"
dump home
tap home "Settings"
sleep 2
dump settings
tap settings "Accounts"
sleep 2
dump accounts
tap accounts "New account"
sleep 2
dump card
tap card "Name" last
sleep 1
adb shell input text Amex
hide_keyboard
dump card
tap card "Credit card"
sleep 1
dump card
fill() { # fill <dump name> <placeholder> <text>
  scroll_to "$1" "$2"
  tap "$1" "$2" last # the field, not its label above it
  sleep 1
  adb shell input text "$3"
  hide_keyboard
  dump "$1"
}
fill card "Amount owed now" 5000
fill card "Credit limit (optional)" 50000
fill card "Statement day" 1
fill card "Due day" 28
shot 12-new-card
scroll_to card "Save"
tap card "Save" last
sleep 2
dump accounts
expect accounts 'text="Amex"' "the card isn't listed"
expect accounts 'text="₹5,000 owed"' "the card doesn't show what's owed"
echo "PASS: adds a credit card with what's owed"
adb shell input keyevent KEYCODE_BACK
sleep 2
dump settings
expect settings 'text="Card bill reminders"' "Settings has no card bill reminders once there's a card"
tap settings "Home"
sleep 2
dump home
scroll_to home "Amex"
expect home 'due' "the card on Home doesn't say when its bill is due"
tap home "Amex"
sleep 2
dump carddetail
shot 13-card
expect carddetail 'text="Credit limit"' "the card's page doesn't show its limit"
expect carddetail 'text="₹45,000"' "the card's page doesn't show the credit available"
scroll_to carddetail "Pay bill"
tap carddetail "Pay bill"
sleep 2
dump pay
shot 14-pay-bill
expect pay 'text="New transfer"' "Pay bill doesn't open a transfer"
expect pay 'text="5000"' "Pay bill doesn't fill in the amount due"
tap pay "Cancel"
sleep 2
echo "PASS: shows the card's bill and pays it"

echo "--- Launcher shortcut"
adb shell am start -W -a io.github.akrishna87.selavu.ADD_EXPENSE -n "$PKG/.MainActivity"
sleep 3
dump shortcut
expect shortcut 'text="New expense"' "the Add expense shortcut didn't open a new expense"
hide_keyboard
shot 7-shortcut
echo "PASS: Add expense shortcut"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
