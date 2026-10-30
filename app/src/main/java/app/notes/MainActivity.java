package app.notes;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executors;

/** The notes screen: notes, reminders, labels, archive, deleted, search and selection. */
public class MainActivity extends AppCompatActivity implements NoteAdapter.Listener {

    static final int NOTES = 0, REMINDERS = 1, LABEL = 2, ARCHIVE = 3, DELETED = 4;
    static final int SORT_CUSTOM = 0, SORT_CREATED = 1, SORT_MODIFIED = 2;
    static final int F_NONE = 0, F_REMINDERS = 1, F_LISTS = 2, F_IMAGES = 3, F_DRAWINGS = 4,
            F_VOICE = 5, F_LABEL = 6, F_COLOR = 7;

    /** Set by App when the app is left, so the next visit starts from a normal state. */
    static boolean resetOnReturn;
    /** Typed into search, opens the hidden settings. */
    static final String SECRET_CODE = "*#*#";
    private static java.lang.ref.WeakReference<MainActivity> live;
    /** A message to show when coming back from the editor, with an optional undo. */
    static String pendingMessage;
    static Runnable pendingUndo;

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DrawerLayout drawer;
    private CoordinatorLayout coordinator;
    private View header, barMain, barSection, barSelect, barSearch, searchBar, scrim;
    private TextView searchHint, sectionTitle, selectCount, emptyText, searchFilter;
    private ImageView emptyIcon;
    private ImageButton viewToggle, sectionViewToggle, sectionMore, selectPin, selectRemind,
            selectColor, selectLabel, selectRestore, searchClear;
    private EditText searchInput;
    private RecyclerView list;
    private StaggeredGridLayoutManager layout;
    private NoteAdapter adapter;
    private ItemTouchHelper touchHelper;
    private SwipeRefreshLayout refresh;
    private View empty;
    private NestedScrollView searchHome, drawerScroll;
    private LinearLayout searchHomeContent, drawerItems, fabMenu;
    private FloatingActionButton fab, fabLive;
    private AvatarView avatar;

    private int section = NOTES;
    private String sectionLabel;
    private boolean searching;
    private String query = "";
    private int filter = F_NONE;
    private String filterLabel;
    private int filterColor;
    private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
    private boolean fabOpen;
    private int navBottom;
    private boolean lifted;
    private android.animation.ValueAnimator liftAnim;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;
    private Recorder recorder;
    private final Keypad keypad = new Keypad();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Never show a screenshot of how the notes looked last time while the app opens: the
        // Celebrities note may have changed since, and the change must not be seen happening.
        setRecentsScreenshotEnabled(false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        live = new java.lang.ref.WeakReference<>(this);
        store = Store.get(this);
        pickImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                this::onImagePicked);
        recorder = new Recorder(this);

        drawer = findViewById(R.id.drawer);
        coordinator = findViewById(R.id.coordinator);
        header = findViewById(R.id.header);
        barMain = findViewById(R.id.bar_main);
        barSection = findViewById(R.id.bar_section);
        barSelect = findViewById(R.id.bar_select);
        barSearch = findViewById(R.id.bar_search);
        searchBar = findViewById(R.id.search_bar);
        searchHint = findViewById(R.id.search_hint);
        viewToggle = findViewById(R.id.view_toggle);
        avatar = findViewById(R.id.avatar);
        sectionTitle = findViewById(R.id.section_title);
        sectionViewToggle = findViewById(R.id.section_view_toggle);
        sectionMore = findViewById(R.id.section_more);
        selectCount = findViewById(R.id.select_count);
        selectPin = findViewById(R.id.select_pin);
        selectRemind = findViewById(R.id.select_remind);
        selectColor = findViewById(R.id.select_color);
        selectLabel = findViewById(R.id.select_label);
        selectRestore = findViewById(R.id.select_restore);
        searchInput = findViewById(R.id.search_input);
        searchFilter = findViewById(R.id.search_filter);
        searchClear = findViewById(R.id.search_clear);
        list = findViewById(R.id.list);
        refresh = findViewById(R.id.refresh);
        empty = findViewById(R.id.empty);
        emptyIcon = findViewById(R.id.empty_icon);
        emptyText = findViewById(R.id.empty_text);
        searchHome = findViewById(R.id.search_home);
        searchHomeContent = findViewById(R.id.search_home_content);
        drawerScroll = findViewById(R.id.drawer_scroll);
        drawerItems = findViewById(R.id.drawer_items);
        fabMenu = findViewById(R.id.fab_menu);
        fab = findViewById(R.id.fab);
        fabLive = findViewById(R.id.fab_live);
        scrim = findViewById(R.id.scrim);

        setupInsets();
        setupHeader();
        setupList();
        setupFab();
        setupSearch();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });
        if (state == null) introduceSearchHint();
    }

    /** A new number was entered: redraw now, while the app is still out of sight. */
    static void numberChanged() {
        MainActivity a = live != null ? live.get() : null;
        if (a == null || a.isDestroyed()) return;
        a.runOnUiThread(() -> {
            if (!a.isDestroyed()) a.render();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bring the Celebrities note up to date before anything is drawn.
        Celebs.refresh(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resetOnReturn) {
            resetOnReturn = false;
            resetState();
        }
        Celebs.refresh(this);
        render();
        buildDrawer();
        if (pendingMessage != null) {
            String msg = pendingMessage;
            Runnable undo = pendingUndo;
            pendingMessage = null;
            pendingUndo = null;
            handler.postDelayed(() -> snack(msg, undo), 250);
        }
    }

    /** Back to the plain notes list, as after a fresh start. */
    private void resetState() {
        closeFab(false);
        drawer.closeDrawer(GravityCompat.START, false);
        selected.clear();
        if (searching) stopSearch();
        section = NOTES;
        sectionLabel = null;
        list.scrollToPosition(0);
    }

    // Layout -----------------------------------------------------------------------------------

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(drawer, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            navBottom = sys.bottom;
            header.setPadding(0, sys.top, 0, 0);
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                    sys.bottom + dp(150));
            searchHome.setPadding(0, 0, 0, Math.max(sys.bottom, ime.bottom) + dp(16));
            drawerScroll.setPadding(0, sys.top, 0, sys.bottom);
            empty.setPadding(0, 0, 0, sys.bottom + dp(80));
            positionFabs();
            return insets;
        });
    }

    /** Measured on Keep: 16dp from the edge, 40dp above the navigation bar, 16dp apart. */
    private void positionFabs() {
        int bottom = navBottom + dp(40);
        CoordinatorLayout.LayoutParams big = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
        big.setMargins(0, 0, fabOpen ? dp(16 + (80 - 56) / 2) : dp(16), fabOpen ? bottom + dp(12) : bottom);
        fab.setLayoutParams(big);
        CoordinatorLayout.LayoutParams small = (CoordinatorLayout.LayoutParams) fabLive.getLayoutParams();
        small.setMargins(0, 0, dp(16), bottom + dp(80 + 16));
        fabLive.setLayoutParams(small);
        CoordinatorLayout.LayoutParams menu = (CoordinatorLayout.LayoutParams) fabMenu.getLayoutParams();
        menu.setMargins(0, 0, dp(16), bottom + dp(12 + 56 + 10));
        fabMenu.setLayoutParams(menu);
    }

    private void setupHeader() {
        searchBar.setBackground(Ui.ripple(this, Ui.rounded(Ui.color(this, R.color.keep_search), dp(28), 0, 0)));
        searchBar.setContentDescription("Search Keep");
        searchBar.setOnClickListener(v -> startSearch());
        findViewById(R.id.menu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.section_menu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        viewToggle.setOnClickListener(v -> toggleView());
        sectionViewToggle.setOnClickListener(v -> toggleView());
        findViewById(R.id.sort).setOnClickListener(this::showSort);
        findViewById(R.id.section_search).setOnClickListener(v -> startSearch());
        sectionMore.setOnClickListener(this::showSectionMenu);
        avatar.setOnClickListener(v -> Sheets.account(this));

        findViewById(R.id.select_close).setOnClickListener(v -> clearSelection());
        selectPin.setOnClickListener(v -> pinSelected());
        selectRemind.setOnClickListener(v -> remindSelected());
        selectColor.setOnClickListener(v -> colorSelected());
        selectColor.setContentDescription(getString(R.string.change_colour));
        selectLabel.setOnClickListener(v -> labelSelected());
        selectRestore.setOnClickListener(v -> restoreSelected());
        findViewById(R.id.select_more).setOnClickListener(this::showSelectionMenu);
        drawer.setDrawerShadow(new android.graphics.drawable.ColorDrawable(0), GravityCompat.START);
        drawer.setScrimColor(Ui.color(this, R.color.keep_scrim));
    }

    /** Google apps first show their name in the search bar, then the search hint. */
    private void introduceSearchHint() {
        searchHint.setText("Google Keep");
        handler.postDelayed(() -> searchHint.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            searchHint.setText("Search Keep");
            searchHint.animate().alpha(1f).setDuration(220).start();
        }).start(), 1600);
    }

    private void setupList() {
        adapter = new NoteAdapter(this, selected, this);
        layout = new StaggeredGridLayoutManager(Prefs.grid(this) ? 2 : 1, StaggeredGridLayoutManager.VERTICAL);
        layout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        list.setLayoutManager(layout);
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                updateLift();
            }
        });
        searchHome.setOnScrollChangeListener((View v, int x, int y, int ox, int oy) -> updateLift());
        touchHelper = new ItemTouchHelper(new TouchCallback());
        touchHelper.attachToRecyclerView(list);
        refresh.setColorSchemeColors(Ui.color(this, R.color.keep_primary));
        refresh.setProgressBackgroundColorSchemeColor(Ui.color(this, R.color.keep_surface_high));
        refresh.setOnRefreshListener(() -> handler.postDelayed(() -> refresh.setRefreshing(false), 1300));
    }

    // FABs -------------------------------------------------------------------------------------

    private void setupFab() {
        fab.setOnClickListener(v -> {
            if (fabOpen) closeFab(true);
            else openFab();
        });
        fabLive.setOnClickListener(v -> snack("Can't connect right now. Check your internet connection and try again.", null));
        scrim.setOnClickListener(v -> closeFab(true));
        String[] labels = {"Audio", "Image", "Drawing", "List", "Text"};
        int[] icons = {R.drawable.ic_mic, R.drawable.ic_image, R.drawable.ic_brush,
                R.drawable.ic_check_box, R.drawable.ic_text_fields};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(20), 0, dp(24), 0);
            item.setBackground(Ui.ripple(this, Ui.rounded(Ui.color(this, R.color.keep_fab_big), dp(28), 0, 0)));
            item.setElevation(dp(3));
            int fg = Ui.color(this, R.color.keep_on_fab_big);
            item.addView(Ui.icon(this, icons[i], fg, 24));
            TextView t = Ui.text(this, labels[i], 16, fg, R.font.google_sans_medium);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            tl.setMarginStart(dp(12));
            item.addView(t, tl);
            item.setContentDescription(labels[i]);
            item.setOnClickListener(v -> {
                closeFab(false);
                create(which);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, dp(56));
            lp.topMargin = dp(6);
            fabMenu.addView(item, lp);
        }
        positionFabs();
    }

    private void openFab() {
        fabOpen = true;
        fab.setCustomSize(dp(56));
        fab.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(dp(28)).build());
        fab.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, R.color.keep_fab_small)));
        fab.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.keep_on_fab_small)));
        fab.setImageResource(R.drawable.ic_close);
        fab.setMaxImageSize(dp(24));
        fab.setContentDescription("Close");
        positionFabs();
        fabLive.hide();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(150).start();
        fabMenu.setVisibility(View.VISIBLE);
        int n = fabMenu.getChildCount();
        for (int i = 0; i < n; i++) {
            View item = fabMenu.getChildAt(i);
            item.setAlpha(0f);
            item.setTranslationY(dp(16));
            item.animate().alpha(1f).translationY(0).setStartDelay((n - 1 - i) * 30L).setDuration(160).start();
        }
    }

    private void closeFab(boolean animate) {
        if (!fabOpen) return;
        fabOpen = false;
        fab.setCustomSize(dp(80));
        fab.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(dp(16)).build());
        fab.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, R.color.keep_fab_big)));
        fab.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.keep_on_fab_big)));
        fab.setImageResource(R.drawable.ic_add);
        fab.setMaxImageSize(dp(36));
        fab.setContentDescription("Create a note");
        positionFabs();
        if (animate) {
            scrim.animate().alpha(0f).setDuration(120).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        } else {
            scrim.setVisibility(View.GONE);
        }
        fabMenu.setVisibility(View.GONE);
        if (section == NOTES && !searching && selected.isEmpty()) fabLive.show();
    }

    /** 0 audio, 1 image, 2 drawing, 3 list, 4 text. */
    private void create(int kind) {
        switch (kind) {
            case 0:
                recorder.record((file, duration) -> {
                    Note n = newNote();
                    n.audio.add(new Note.Audio(file, duration, System.currentTimeMillis()));
                    store.changed();
                    openEditor(n.id);
                });
                break;
            case 1:
                App.external = true;
                pickImage.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                break;
            case 2:
                startActivity(new Intent(this, DrawingActivity.class)
                        .putExtra(EditorActivity.EXTRA_LABEL, section == LABEL ? sectionLabel : null));
                break;
            case 3:
            case 4:
                startActivity(new Intent(this, EditorActivity.class)
                        .putExtra(EditorActivity.EXTRA_NEW, true)
                        .putExtra(EditorActivity.EXTRA_LIST, kind == 3)
                        .putExtra(EditorActivity.EXTRA_LABEL, section == LABEL ? sectionLabel : null));
                break;
        }
    }

    private Note newNote() {
        Note n = store.create();
        if (section == LABEL && sectionLabel != null) n.labels.add(sectionLabel);
        n.edited = n.created + 1;
        return n;
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            String name = Media.importImage(this, uri);
            runOnUiThread(() -> {
                if (name == null) {
                    snack("Couldn't add the image", null);
                    return;
                }
                Note n = newNote();
                n.attachments.add(new Note.Attachment(name, Note.IMAGE));
                store.changed();
                openEditor(n.id);
            });
        });
    }

    private void openEditor(long id) {
        startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, id));
    }

    // Search -----------------------------------------------------------------------------------

    private void setupSearch() {
        LinearLayout field = findViewById(R.id.search_field);
        field.setBackground(Ui.rounded(Ui.color(this, R.color.keep_search), dp(28), 0, 0));
        findViewById(R.id.search_back).setOnClickListener(v -> stopSearch());
        searchClear.setOnClickListener(v -> {
            searchInput.setText("");
            if (filter != F_NONE && query.isEmpty()) clearFilter();
        });
        searchFilter.setBackground(Ui.rounded(Ui.color(this, R.color.keep_selected), dp(17), 0, 0));
        searchFilter.setOnClickListener(v -> clearFilter());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!searching) return;
                if (SECRET_CODE.equals(s.toString().trim())) {
                    stopSearch();
                    startActivity(new Intent(MainActivity.this, HiddenSettingsActivity.class));
                    return;
                }
                query = s.toString();
                render();
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void startSearch() {
        clearSelection();
        searching = true;
        query = "";
        filter = F_NONE;
        searchInput.setText("");
        render();
        searchInput.requestFocus();
        searchInput.post(() -> getSystemService(InputMethodManager.class)
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT));
    }

    private void stopSearch() {
        searching = false;
        query = "";
        filter = F_NONE;
        hideKeyboard();
        searchInput.setText("");
        render();
    }

    private void setFilter(int f, String label, int color) {
        filter = f;
        filterLabel = label;
        filterColor = color;
        hideKeyboard();
        render();
    }

    private void clearFilter() {
        filter = F_NONE;
        render();
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private boolean matches(Note n) {
        String q = query.trim().toLowerCase();
        if (!q.isEmpty() && !n.searchText().contains(q)) return false;
        switch (filter) {
            case F_REMINDERS: return n.reminder > 0;
            case F_LISTS: return n.list;
            case F_IMAGES: return n.hasImage();
            case F_DRAWINGS: return n.hasDrawing();
            case F_VOICE: return !n.audio.isEmpty();
            case F_LABEL: return n.labels.contains(filterLabel);
            case F_COLOR: return n.color == filterColor;
            default: return true;
        }
    }

    private String filterName() {
        switch (filter) {
            case F_REMINDERS: return "Reminders";
            case F_LISTS: return "Lists";
            case F_IMAGES: return "Images";
            case F_DRAWINGS: return "Drawings";
            case F_VOICE: return "Voice";
            case F_LABEL: return filterLabel;
            case F_COLOR: return Ui.COLOR_NAMES[filterColor];
            default: return "";
        }
    }

    private void buildSearchHome() {
        searchHomeContent.removeAllViews();
        boolean reminders = false, lists = false, images = false, drawings = false, voice = false;
        boolean[] colors = new boolean[Ui.COLOR_NAMES.length];
        for (Note n : store.notes()) {
            if (n.trashed || (n.isEmpty() && !n.isCelebrities())) continue;
            reminders |= n.reminder > 0;
            lists |= n.list;
            images |= n.hasImage();
            drawings |= n.hasDrawing();
            voice |= !n.audio.isEmpty();
            colors[n.color] = true;
        }
        Wrap types = new Wrap(this, dp(8), dp(8));
        if (reminders) types.addView(pill(R.drawable.ic_notifications, "Reminders", v -> setFilter(F_REMINDERS, null, 0)));
        if (lists) types.addView(pill(R.drawable.ic_check_box, "Lists", v -> setFilter(F_LISTS, null, 0)));
        if (images) types.addView(pill(R.drawable.ic_image, "Images", v -> setFilter(F_IMAGES, null, 0)));
        if (drawings) types.addView(pill(R.drawable.ic_brush, "Drawings", v -> setFilter(F_DRAWINGS, null, 0)));
        if (voice) types.addView(pill(R.drawable.ic_mic, "Voice", v -> setFilter(F_VOICE, null, 0)));
        if (types.getChildCount() > 0) {
            searchHomeContent.addView(searchHeading("Types"));
            searchHomeContent.addView(types);
        }
        if (!store.labels().isEmpty()) {
            Wrap labels = new Wrap(this, dp(8), dp(8));
            for (String l : store.labels()) labels.addView(pill(R.drawable.ic_label, l, v -> setFilter(F_LABEL, l, 0)));
            searchHomeContent.addView(searchHeading("Labels"));
            searchHomeContent.addView(labels);
        }
        Wrap swatches = new Wrap(this, dp(12), dp(12));
        for (int i = 0; i < colors.length; i++) {
            if (!colors[i]) continue;
            final int color = i;
            View dot = new View(this);
            int fill = Ui.noteColor(this, i);
            dot.setBackground(Ui.ripple(this, Ui.rounded(fill, dp(24), Ui.color(this, R.color.keep_stroke), dp(1.5f))));
            dot.setContentDescription(Ui.COLOR_NAMES[i]);
            dot.setLayoutParams(new ViewGroup.LayoutParams(dp(48), dp(48)));
            dot.setOnClickListener(v -> setFilter(F_COLOR, null, color));
            swatches.addView(dot);
        }
        if (swatches.getChildCount() > 0) {
            searchHomeContent.addView(searchHeading(getString(R.string.colours)));
            searchHomeContent.addView(swatches);
        }
    }

    private TextView searchHeading(String s) {
        TextView t = Ui.text(this, s, 14, Ui.muted(this), R.font.google_sans_medium);
        t.setPadding(dp(4), dp(20), 0, dp(12));
        return t;
    }

    private View pill(int icon, String label, View.OnClickListener click) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(16), 0, dp(20), 0);
        p.setMinimumHeight(dp(48));
        p.setBackground(Ui.ripple(this, Ui.rounded(Ui.color(this, R.color.keep_search), dp(24), 0, 0)));
        p.addView(Ui.icon(this, icon, Ui.textColor(this), 22));
        TextView t = Ui.text(this, label, 15, Ui.textColor(this), R.font.google_sans_medium);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(dp(10));
        p.addView(t, lp);
        p.setContentDescription(label);
        p.setOnClickListener(click);
        p.setLayoutParams(new ViewGroup.LayoutParams(Ui.WRAP, dp(48)));
        return p;
    }

    // Drawer -----------------------------------------------------------------------------------

    private void buildDrawer() {
        drawerItems.removeAllViews();
        TextView brand = Ui.text(this, "Google Keep", 22, Ui.textColor(this), R.font.google_sans_regular);
        brand.setPadding(dp(28), dp(22), dp(16), dp(18));
        drawerItems.addView(brand);
        drawerItem(R.drawable.ic_lightbulb, "Notes", !searching && section == NOTES, v -> go(NOTES, null));
        drawerItem(R.drawable.ic_notifications, "Reminders", !searching && section == REMINDERS, v -> go(REMINDERS, null));
        if (!store.labels().isEmpty()) {
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(28), dp(10), dp(12), 0);
            TextView t = Ui.text(this, "Labels", 14, Ui.muted(this), R.font.google_sans_medium);
            head.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            TextView edit = Ui.text(this, "Edit", 14, Ui.textColor(this), R.font.google_sans_medium);
            edit.setPadding(dp(16), dp(12), dp(16), dp(12));
            edit.setBackground(Ui.rippleRounded(this, dp(20)));
            edit.setOnClickListener(v -> openLabels(false));
            head.addView(edit);
            drawerItems.addView(head);
            for (String l : store.labels()) {
                drawerItem(R.drawable.ic_label, l, !searching && section == LABEL && l.equals(sectionLabel), v -> go(LABEL, l));
            }
        }
        drawerItem(R.drawable.ic_add, "Create new label", false, v -> openLabels(true));
        drawerItem(R.drawable.ic_archive, "Archive", !searching && section == ARCHIVE, v -> go(ARCHIVE, null));
        drawerItem(R.drawable.ic_delete, "Deleted", !searching && section == DELETED, v -> go(DELETED, null));
        drawerItem(R.drawable.ic_settings, "Settings", false, v -> {
            drawer.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        drawerItem(R.drawable.ic_help, "Help & feedback", false, v -> {
            drawer.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, HelpActivity.class));
        });
    }

    private void drawerItem(int icon, String label, boolean active, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(24), 0);
        int fg = active ? Ui.color(this, R.color.keep_on_selected) : Ui.textColor(this);
        if (active) {
            row.setBackground(Ui.ripple(this, Ui.rounded(Ui.color(this, R.color.keep_selected), dp(28), 0, 0)));
        } else {
            row.setBackground(Ui.rippleRounded(this, dp(28)));
        }
        row.addView(Ui.icon(this, icon, fg, 24));
        TextView t = Ui.text(this, label, 15, fg, R.font.google_sans_medium);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tl.setMarginStart(dp(14));
        row.addView(t, tl);
        row.setOnClickListener(click);
        row.setContentDescription(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.MATCH, dp(56));
        lp.setMargins(dp(12), 0, dp(12), 0);
        drawerItems.addView(row, lp);
    }

    private void go(int s, String label) {
        drawer.closeDrawer(GravityCompat.START);
        boolean changed = s != section || (label != null && !label.equals(sectionLabel)) || searching;
        section = s;
        sectionLabel = label;
        selected.clear();
        if (searching) {
            searching = false;
            hideKeyboard();
        }
        closeFab(false);
        render();
        buildDrawer();
        if (changed) list.scrollToPosition(0);
    }

    private void openLabels(boolean create) {
        drawer.closeDrawer(GravityCompat.START);
        startActivity(new Intent(this, LabelsActivity.class).putExtra(LabelsActivity.EXTRA_CREATE, create));
    }

    // Rendering --------------------------------------------------------------------------------

    private void render() {
        boolean selecting = !selected.isEmpty();
        barSelect.setVisibility(selecting ? View.VISIBLE : View.GONE);
        barSearch.setVisibility(!selecting && searching ? View.VISIBLE : View.GONE);
        barMain.setVisibility(!selecting && !searching && section == NOTES ? View.VISIBLE : View.GONE);
        barSection.setVisibility(!selecting && !searching && section != NOTES ? View.VISIBLE : View.GONE);

        boolean grid = Prefs.grid(this);
        int toggleIcon = grid ? R.drawable.ic_view_agenda : R.drawable.ic_grid_view;
        String toggleDesc = grid ? "List view" : "Grid view";
        viewToggle.setImageResource(toggleIcon);
        viewToggle.setContentDescription(toggleDesc);
        sectionViewToggle.setImageResource(toggleIcon);
        sectionViewToggle.setContentDescription(toggleDesc);
        if (layout.getSpanCount() != (grid ? 2 : 1)) layout.setSpanCount(grid ? 2 : 1);
        adapter.grid = grid;

        if (section == LABEL && sectionLabel != null && !store.labels().contains(sectionLabel)) {
            section = NOTES;
            sectionLabel = null;
        }
        sectionTitle.setText(sectionName());
        sectionMore.setVisibility(section == DELETED || section == LABEL ? View.VISIBLE : View.GONE);

        // Search field
        searchFilter.setVisibility(searching && filter != F_NONE ? View.VISIBLE : View.GONE);
        if (filter != F_NONE) {
            searchFilter.setText(filterName());
            android.graphics.drawable.Drawable x = getDrawable(R.drawable.ic_close).mutate();
            x.setBounds(0, 0, dp(18), dp(18));
            x.setTint(Ui.color(this, R.color.keep_on_selected));
            searchFilter.setCompoundDrawablesRelative(null, null, x, null);
        }
        searchInput.setHint(filter != F_NONE ? "" : "Search Keep");
        searchClear.setVisibility(searching && (!query.isEmpty()) ? View.VISIBLE : View.GONE);

        boolean home = searching && query.trim().isEmpty() && filter == F_NONE;
        searchHome.setVisibility(home ? View.VISIBLE : View.GONE);
        if (home) buildSearchHome();
        List<Object> items = home ? new ArrayList<>() : buildItems();
        adapter.submit(items);
        list.setVisibility(home ? View.GONE : View.VISIBLE);

        boolean noNotes = !home && !hasNotes(items);
        empty.setVisibility(noNotes ? View.VISIBLE : View.GONE);
        if (noNotes) showEmpty();

        updateSelectionBar();
        boolean fabs = !selecting && !searching && (section == NOTES || section == LABEL || section == REMINDERS);
        if (fabs) fab.show();
        else fab.hide();
        if (fabs && section == NOTES && !fabOpen) fabLive.show();
        else fabLive.hide();
        refresh.setEnabled(!selecting && !searching && section == NOTES);
        list.post(this::updateLift);
    }

    /**
     * Like Keep, the top bar is the background colour while the content is at the top, and
     * turns a lighter grey only while something is scrolled underneath it.
     */
    private void updateLift() {
        boolean scrolled = searchHome.getVisibility() == View.VISIBLE
                ? searchHome.canScrollVertically(-1)
                : list.getVisibility() == View.VISIBLE && list.canScrollVertically(-1);
        if (scrolled == lifted && liftAnim == null && header.getBackground() != null) return;
        lifted = scrolled;
        int from = ((android.graphics.drawable.ColorDrawable) header.getBackground()).getColor();
        int to = Ui.color(this, scrolled ? R.color.keep_header : R.color.keep_bg);
        if (liftAnim != null) liftAnim.cancel();
        if (from == to) {
            liftAnim = null;
            return;
        }
        liftAnim = android.animation.ValueAnimator.ofArgb(from, to);
        liftAnim.setDuration(150);
        liftAnim.addUpdateListener(a -> header.setBackgroundColor((int) a.getAnimatedValue()));
        liftAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                liftAnim = null;
            }
        });
        liftAnim.start();
    }

    private boolean hasNotes(List<Object> items) {
        for (Object o : items) if (o instanceof Note) return true;
        return false;
    }

    private String sectionName() {
        switch (section) {
            case REMINDERS: return "Reminders";
            case LABEL: return sectionLabel;
            case ARCHIVE: return "Archive";
            case DELETED: return "Deleted";
            default: return "Notes";
        }
    }

    private void showEmpty() {
        int icon;
        String text;
        if (searching) {
            icon = R.drawable.ic_search;
            text = "No matching results";
        } else {
            switch (section) {
                case REMINDERS:
                    icon = R.drawable.ic_notifications;
                    text = "Notes with upcoming reminders appear here";
                    break;
                case LABEL:
                    icon = R.drawable.ic_label;
                    text = "No notes with this label yet";
                    break;
                case ARCHIVE:
                    icon = R.drawable.ic_archive;
                    text = "Your archived notes appear here";
                    break;
                case DELETED:
                    icon = R.drawable.ic_delete;
                    text = "No notes in Deleted";
                    break;
                default:
                    icon = R.drawable.ic_lightbulb;
                    text = "Notes you add appear here";
            }
        }
        emptyIcon.setImageResource(icon);
        emptyText.setText(text);
    }

    private List<Object> buildItems() {
        List<Note> pool = new ArrayList<>();
        for (Note n : store.notes()) {
            if (n.isEmpty() && !n.isCelebrities()) continue;
            boolean in;
            if (searching) {
                in = !n.trashed && matches(n);
            } else {
                switch (section) {
                    case REMINDERS: in = !n.trashed && n.reminder > 0; break;
                    case LABEL: in = !n.trashed && !n.archived && n.labels.contains(sectionLabel); break;
                    case ARCHIVE: in = !n.trashed && n.archived; break;
                    case DELETED: in = n.trashed; break;
                    default: in = !n.trashed && !n.archived;
                }
            }
            if (in) pool.add(n);
        }
        List<Object> out = new ArrayList<>();
        if (!searching && section == DELETED) {
            pool.sort((a, b) -> Long.compare(b.trashedAt, a.trashedAt));
            if (!pool.isEmpty()) {
                out.add(new NoteAdapter.Header("Notes in Deleted are deleted after 7 days.", true));
            }
            out.addAll(pool);
            return out;
        }
        if (!searching && section == REMINDERS) {
            long now = System.currentTimeMillis();
            List<Note> upcoming = new ArrayList<>(), sent = new ArrayList<>();
            for (Note n : pool) {
                if (n.reminder >= now && !n.reminderDone) upcoming.add(n);
                else sent.add(n);
            }
            upcoming.sort(Comparator.comparingLong(n -> n.reminder));
            sent.sort((a, b) -> Long.compare(b.reminder, a.reminder));
            if (!upcoming.isEmpty()) {
                out.add(new NoteAdapter.Header("Upcoming", false));
                out.addAll(upcoming);
            }
            if (!sent.isEmpty()) {
                out.add(new NoteAdapter.Header("Sent", false));
                out.addAll(sent);
            }
            return out;
        }
        pool.sort(order());
        if (searching || section == ARCHIVE) {
            out.addAll(pool);
            return out;
        }
        List<Note> pinned = new ArrayList<>(), others = new ArrayList<>();
        for (Note n : pool) (n.pinned ? pinned : others).add(n);
        if (!pinned.isEmpty()) {
            out.add(new NoteAdapter.Header("Pinned", false));
            out.addAll(pinned);
            if (!others.isEmpty()) out.add(new NoteAdapter.Header("Others", false));
        }
        out.addAll(others);
        return out;
    }

    private Comparator<Note> order() {
        int sort = Prefs.sort(this);
        Comparator<Note> c;
        if (sort == SORT_CREATED) c = Comparator.comparingLong(n -> n.created);
        else if (sort == SORT_MODIFIED) c = Comparator.comparingLong(n -> n.edited);
        else c = Comparator.comparingLong(n -> n.order);
        c = c.reversed();
        if (sort != SORT_CUSTOM && Prefs.sortReversed(this)) c = c.reversed();
        return c;
    }

    private void toggleView() {
        Prefs.setGrid(this, !Prefs.grid(this));
        render();
    }

    private void showSort(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor, Gravity.END);
        int sort = Prefs.sort(this);
        boolean rev = Prefs.sortReversed(this);
        String arrow = rev ? "  ↑" : "  ↓";
        m.getMenu().add(0, SORT_CUSTOM, 0, "Custom");
        m.getMenu().add(0, SORT_CREATED, 1, "Date created" + (sort == SORT_CREATED ? arrow : ""));
        m.getMenu().add(0, SORT_MODIFIED, 2, "Date modified" + (sort == SORT_MODIFIED ? arrow : ""));
        m.getMenu().setGroupCheckable(0, true, true);
        m.getMenu().findItem(sort).setChecked(true);
        m.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == sort && id != SORT_CUSTOM) Prefs.setSort(this, id, !rev);
            else Prefs.setSort(this, id, false);
            render();
            return true;
        });
        m.show();
    }

    private void showSectionMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor, Gravity.END);
        if (section == DELETED) {
            m.getMenu().add(0, 1, 0, "Empty Deleted");
        } else if (section == LABEL) {
            m.getMenu().add(0, 2, 0, "Rename label");
            m.getMenu().add(0, 3, 1, "Delete label");
        }
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    emptyDeleted();
                    break;
                case 2:
                    Sheets.prompt(this, "Rename label", sectionLabel, name -> {
                        if (store.renameLabel(sectionLabel, name)) {
                            sectionLabel = name.trim();
                            render();
                            buildDrawer();
                        }
                    });
                    break;
                case 3:
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete label?")
                            .setMessage("We’ll delete this label and remove it from all of your Keep notes. Your notes won’t be deleted.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> {
                                store.deleteLabel(sectionLabel);
                                go(NOTES, null);
                            })
                            .show();
                    break;
            }
            return true;
        });
        m.show();
    }

    private void emptyDeleted() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Empty Deleted?")
                .setMessage("All notes in Deleted will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Empty Deleted", (d, w) -> {
                    for (Note n : new ArrayList<>(store.notes())) {
                        if (!n.trashed) continue;
                        if (n.isCelebrities()) continue;
                        store.deleteForever(n);
                    }
                    render();
                })
                .show();
    }

    // Cards ------------------------------------------------------------------------------------

    @Override
    public void onClick(Note n) {
        if (!selected.isEmpty()) {
            toggle(n);
            return;
        }
        openEditor(n.id);
    }

    @Override
    public void onLongClick(Note n, RecyclerView.ViewHolder holder) {
        list.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (!selected.contains(n.id)) toggle(n);
        if (canDrag()) touchHelper.startDrag(holder);
    }

    @Override
    public boolean canSwipe() {
        return selected.isEmpty() && !searching
                && (section == NOTES || section == LABEL || section == ARCHIVE || section == REMINDERS);
    }

    @Override
    public void onSwiped(Note n) {
        List<Note> one = new ArrayList<>();
        one.add(n);
        archive(one, !n.archived);
    }

    private boolean canDrag() {
        return !searching && Prefs.sort(this) == SORT_CUSTOM && (section == NOTES || section == LABEL);
    }

    private void toggle(Note n) {
        if (!selected.remove(n.id)) selected.add(n.id);
        if (selected.isEmpty()) {
            render();
            return;
        }
        closeFab(false);
        int pos = adapter.items.indexOf(n);
        updateSelectionBar();
        if (barSelect.getVisibility() != View.VISIBLE) render();
        else if (pos >= 0) adapter.notifyItemChanged(pos);
    }

    private void clearSelection() {
        if (selected.isEmpty()) return;
        selected.clear();
        render();
    }

    private List<Note> selectedNotes() {
        List<Note> out = new ArrayList<>();
        for (long id : selected) {
            Note n = store.get(id);
            if (n != null) out.add(n);
        }
        return out;
    }

    private void updateSelectionBar() {
        if (selected.isEmpty()) return;
        selectCount.setText(String.valueOf(selected.size()));
        boolean deleted = !searching && section == DELETED;
        boolean allPinned = true;
        for (Note n : selectedNotes()) allPinned &= n.pinned;
        selectPin.setImageResource(allPinned ? R.drawable.ic_push_pin_fill1 : R.drawable.ic_push_pin);
        selectPin.setContentDescription(allPinned ? "Unpin" : "Pin");
        selectPin.setVisibility(deleted ? View.GONE : View.VISIBLE);
        selectRemind.setVisibility(deleted ? View.GONE : View.VISIBLE);
        selectColor.setVisibility(deleted ? View.GONE : View.VISIBLE);
        selectLabel.setVisibility(deleted ? View.GONE : View.VISIBLE);
        selectRestore.setVisibility(deleted ? View.VISIBLE : View.GONE);
    }

    private void pinSelected() {
        List<Note> ns = selectedNotes();
        boolean allPinned = true;
        for (Note n : ns) allPinned &= n.pinned;
        boolean unarchived = false;
        for (Note n : ns) {
            n.pinned = !allPinned;
            if (n.pinned && n.archived) {
                n.archived = false;
                unarchived = true;
            }
        }
        store.changed();
        selected.clear();
        render();
        if (unarchived) snack(ns.size() == 1 ? "Note unarchived and pinned" : "Notes unarchived and pinned", null);
    }

    private void remindSelected() {
        List<Note> ns = selectedNotes();
        Sheets.reminder(this, ns.size() == 1 ? ns.get(0) : null, Ui.color(this, R.color.keep_card), (when, repeat) -> {
            for (Note n : ns) {
                n.reminder = when;
                n.repeat = repeat;
                n.reminderDone = false;
                Reminders.schedule(this, n);
            }
            store.changed();
            selected.clear();
            render();
        });
    }

    private void colorSelected() {
        List<Note> ns = selectedNotes();
        int current = ns.get(0).color;
        for (Note n : ns) if (n.color != current) current = -1;
        Sheets.colors(this, current, Ui.color(this, R.color.keep_card), color -> {
            for (Note n : ns) n.color = color;
            store.changed();
            render();
        });
    }

    private void labelSelected() {
        long[] ids = new long[selected.size()];
        int i = 0;
        for (long id : selected) ids[i++] = id;
        startActivity(new Intent(this, LabelsActivity.class).putExtra(LabelsActivity.EXTRA_NOTES, ids));
    }

    private void restoreSelected() {
        List<Note> ns = selectedNotes();
        for (Note n : ns) {
            n.trashed = false;
            Reminders.schedule(this, n);
        }
        store.changed();
        selected.clear();
        render();
        snack(ns.size() == 1 ? "Note restored" : ns.size() + " notes restored", () -> {
            for (Note n : ns) {
                n.trashed = true;
                Reminders.cancel(this, n);
            }
            store.changed();
            render();
        });
    }

    private void showSelectionMenu(View anchor) {
        List<Note> ns = selectedNotes();
        PopupMenu m = new PopupMenu(this, anchor, Gravity.END);
        Menu menu = m.getMenu();
        boolean deleted = !searching && section == DELETED;
        if (deleted) {
            menu.add(0, 10, 0, "Delete forever");
        } else {
            boolean allArchived = true;
            for (Note n : ns) allArchived &= n.archived;
            menu.add(0, allArchived ? 2 : 1, 0, allArchived ? "Unarchive" : "Archive");
            menu.add(0, 3, 1, "Delete");
            if (ns.size() == 1) {
                menu.add(0, 4, 2, "Make a copy");
                menu.add(0, 5, 3, "Send");
            }
        }
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: archive(ns, true); break;
                case 2: archive(ns, false); break;
                case 3: trash(ns); break;
                case 4:
                    store.duplicate(ns.get(0));
                    selected.clear();
                    render();
                    break;
                case 5:
                    Sheets.send(this, ns.get(0));
                    selected.clear();
                    render();
                    break;
                case 10:
                    new MaterialAlertDialogBuilder(this)
                            .setMessage(ns.size() == 1 ? "Delete this note forever?" : "Delete these notes forever?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> {
                                for (Note n : ns) store.deleteForever(n);
                                selected.clear();
                                render();
                            })
                            .show();
                    break;
            }
            return true;
        });
        m.show();
    }

    void archive(List<Note> ns, boolean archive) {
        List<boolean[]> before = new ArrayList<>();
        for (Note n : ns) {
            before.add(new boolean[]{n.archived, n.pinned});
            n.archived = archive;
            if (archive) n.pinned = false;
        }
        store.changed();
        selected.clear();
        render();
        String msg = archive ? (ns.size() == 1 ? "Note archived" : ns.size() + " notes archived")
                : (ns.size() == 1 ? "Note unarchived" : ns.size() + " notes unarchived");
        snack(msg, () -> {
            for (int i = 0; i < ns.size(); i++) {
                ns.get(i).archived = before.get(i)[0];
                ns.get(i).pinned = before.get(i)[1];
            }
            store.changed();
            render();
        });
    }

    void trash(List<Note> ns) {
        long now = System.currentTimeMillis();
        for (Note n : ns) {
            n.trashed = true;
            n.trashedAt = now;
            Reminders.cancel(this, n);
        }
        store.changed();
        selected.clear();
        render();
        snack(ns.size() == 1 ? "Note moved to Deleted" : ns.size() + " notes moved to Deleted", () -> {
            for (Note n : ns) {
                n.trashed = false;
                Reminders.schedule(this, n);
            }
            store.changed();
            render();
        });
    }

    private void snack(String msg, Runnable undo) {
        Snackbar s = Snackbar.make(coordinator, msg, Snackbar.LENGTH_LONG);
        if (undo != null) s.setAction("Undo", v -> undo.run());
        s.show();
    }

    // Swipe to archive and drag to reorder ------------------------------------------------------

    private final class TouchCallback extends ItemTouchHelper.Callback {
        private boolean moved;

        @Override
        public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder h) {
            int pos = h.getBindingAdapterPosition();
            if (pos < 0 || !(adapter.items.get(pos) instanceof Note)) return 0;
            int drag = canDrag() ? ItemTouchHelper.UP | ItemTouchHelper.DOWN
                    | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT : 0;
            // Swiping cards away is handled by the cards themselves (NoteAdapter.SwipeAway).
            return makeMovementFlags(drag, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
            int a = from.getBindingAdapterPosition(), b = to.getBindingAdapterPosition();
            if (a < 0 || b < 0) return false;
            Object x = adapter.items.get(a), y = adapter.items.get(b);
            if (!(x instanceof Note) || !(y instanceof Note)) return false;
            if (((Note) x).pinned != ((Note) y).pinned) return false;
            Collections.swap(adapter.items, a, b);
            adapter.notifyItemMoved(a, b);
            moved = true;
            // A drag is not a selection.
            if (selected.size() == 1) {
                selected.clear();
                updateSelectionBar();
                barSelect.setVisibility(View.GONE);
                barMain.setVisibility(section == NOTES ? View.VISIBLE : View.GONE);
                barSection.setVisibility(section != NOTES ? View.VISIBLE : View.GONE);
            }
            return true;
        }

        @Override
        public void clearView(RecyclerView rv, RecyclerView.ViewHolder h) {
            super.clearView(rv, h);
            h.itemView.setAlpha(1f);
            if (!moved) return;
            moved = false;
            List<Note> visible = new ArrayList<>();
            for (Object o : adapter.items) if (o instanceof Note) visible.add((Note) o);
            List<Long> orders = new ArrayList<>();
            for (Note n : visible) orders.add(n.order);
            orders.sort(Collections.reverseOrder());
            for (int i = 0; i < visible.size(); i++) visible.get(i).order = orders.get(i);
            store.changed();
            render();
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder h, int direction) {
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView rv, RecyclerView.ViewHolder h, float dX, float dY,
                                int state, boolean active) {
            if (state == ItemTouchHelper.ACTION_STATE_SWIPE) {
                float w = h.itemView.getWidth();
                h.itemView.setAlpha(Math.max(0.15f, 1f - Math.abs(dX) / w));
            }
            super.onChildDraw(c, rv, h, dX, dY, state, active);
        }
    }

    // Back -------------------------------------------------------------------------------------

    private void onBack() {
        if (fabOpen) {
            closeFab(true);
        } else if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!selected.isEmpty()) {
            clearSelection();
        } else if (searching) {
            if (filter != F_NONE && query.isEmpty()) clearFilter();
            else stopSearch();
        } else if (section != NOTES) {
            go(NOTES, null);
        } else {
            moveTaskToBack(true);
        }
    }

    // Entering the number inside the app, when the home-screen service is off -------------------

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        keypad.onTouch(e);
        return super.dispatchTouchEvent(e);
    }

    /**
     * Fallback for phones where the home-screen swipes can't be read: with the accessibility
     * service turned off, two vertical swipes on the notes list enter the number instead. The
     * keypad is the same: where each swipe starts on the screen picks the digit.
     */
    private final class Keypad {
        private float downX, downY;
        private boolean tracking;

        void onTouch(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    tracking = active() && insideList(e);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (!tracking) break;
                    tracking = false;
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.abs(dy) > dp(72) && Math.abs(dy) > 1.5f * Math.abs(dx)) swiped();
                    break;
            }
        }

        private boolean active() {
            return section == NOTES && !searching && selected.isEmpty() && !fabOpen
                    && !drawer.isDrawerOpen(GravityCompat.START) && !serviceEnabled()
                    && Force.armed(MainActivity.this);
        }

        private boolean insideList(MotionEvent e) {
            Rect r = new Rect();
            list.getGlobalVisibleRect(r);
            return r.contains((int) e.getRawX(), (int) e.getRawY());
        }

        private void swiped() {
            android.graphics.Rect b = getWindowManager().getMaximumWindowMetrics().getBounds();
            int d = GestureService.digitFor(downX, downY, b.width(), b.height(),
                    Force.keypadBottom(MainActivity.this));
            Force.addDigit(MainActivity.this, d);
        }
    }

    private boolean serviceEnabled() {
        AccessibilityManager am = getSystemService(AccessibilityManager.class);
        ComponentName me = new ComponentName(this, GestureService.class);
        for (AccessibilityServiceInfo info :
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                    && me.getClassName().equals(info.getResolveInfo().serviceInfo.name)
                    && me.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
