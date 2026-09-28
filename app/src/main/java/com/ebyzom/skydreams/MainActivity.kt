package com.ebyzom.skydreams

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var game: SkyDreamView
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(23, 18, 65)))

        // Initialize Google Mobile Ads SDK on a background thread
        kotlin.concurrent.thread(name = "admob-init", isDaemon = true) {
            try {
                MobileAds.initialize(this) {}
            } catch (_: Exception) {}
        }

        // Root container: Game area strictly positioned above the bottom banner
        val rootLayout = RelativeLayout(this).apply {
            setBackgroundColor(Color.rgb(23, 18, 65))
        }

        // AdMob Banner anchored at the bottom
        val banner = AdView(this).apply {
            id = View.generateViewId()
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-7470413991742442/2130561586"
        }
        adView = banner

        val adParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            addRule(RelativeLayout.CENTER_HORIZONTAL)
        }

        // Game view fills the area above the banner so no game content or touches overlap the ad!
        game = SkyDreamView(this)
        val gameParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.ABOVE, banner.id)
        }

        rootLayout.addView(game, gameParams)
        rootLayout.addView(banner, adParams)

        setContentView(rootLayout)

        // Request ad
        banner.loadAd(AdRequest.Builder().build())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
        game.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
        game.onActivityResume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
        game.onDestroy()
    }
}

// ---------------------------------------------------------------- models

private const val T_NORMAL = 0
private const val T_MOVING = 1
private const val T_BOOST = 2

private class Platform(
    var x: Float, var y: Float, val w: Float, val h: Float,
    val type: Int, val hue: Int, val baseX: Float, var phase: Float, val speed: Float,
    val range: Float
)

private class Pickup(val x: Float, val y: Float, val gem: Boolean, val phase: Float) {
    var taken = false
}

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Int,
    val size: Float, val confetti: Boolean, var spin: Float, var rot: Float
)

private class Ring(
    var x: Float, var y: Float, var r: Float, val maxR: Float,
    var life: Float, val color: Int
)

private class FloatText(
    var x: Float, var y: Float, val text: String, val color: Int,
    var life: Float
)

private class TrailDot(var x: Float, var y: Float, var life: Float)

private class GameButton(
    var label: String, var w: Float, var h: Float,
    val colorTop: Int, val colorBot: Int
) {
    var x = 0f
    var y = 0f
    var press = 0f

    fun layout(cx: Float, cy: Float) {
        x = cx
        y = cy
    }

    fun hit(px: Float, py: Float, touchPadding: Float = 24f): Boolean =
        abs(px - x) <= w / 2 + touchPadding && abs(py - y) <= h / 2 + touchPadding
}

// ---------------------------------------------------------------- palette

private object C {
    val NAVY = Color.rgb(23, 18, 65)
    val SKY_TOP = Color.rgb(26, 21, 80)
    val SKY_MID = Color.rgb(106, 63, 191)
    val SKY_PINK = Color.rgb(255, 111, 181)
    val SKY_BOT = Color.rgb(255, 177, 214)
    val PINK = Color.rgb(255, 104, 166)
    val PINK_LIGHT = Color.rgb(255, 138, 190)
    val PURPLE = Color.rgb(104, 91, 190)
    val PURPLE_LIGHT = Color.rgb(133, 121, 220)
    val GOLD = Color.rgb(255, 229, 119)
    val GOLD_DEEP = Color.rgb(255, 196, 60)
    val WHITE_SOFT = Color.rgb(255, 244, 252)
    val PANEL = Color.argb(160, 27, 20, 78)
    val TEXT_SOFT = Color.rgb(255, 224, 246)
}

// ---------------------------------------------------------------- the game view

private class SkyDreamView(context: Context) : View(context) {

    // display density scaling factor to guarantee crisp, well-proportioned UI on all screens
    private val dp: Float = context.resources.displayMetrics.density.coerceIn(1.0f, 4.0f)

    // physics constants scaled for screen density
    private val jumpSpeed: Float get() = 620f * dp
    private val boostSpeed: Float get() = 980f * dp
    private val flutterSpeed: Float get() = 540f * dp
    private val gravity: Float get() = 850f * dp
    private val maxSpeed: Float get() = 430f * dp

    // paints
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0))
    }
    private val textMed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
        setShadowLayer(3f, 0f, 1.5f, Color.argb(160, 0, 0, 0))
    }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        setShadowLayer(4f, 0f, 2f, Color.argb(190, 0, 0, 0))
    }

    // audio
    private val snd = SoundManager(context)

    // cached glow sprite
    private val glowBmp: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    private val glowPaints = HashMap<Int, Paint>()

    // sparkle path
    private val sparklePath = Path()

    // state machine: 0 menu, 1 playing, 2 gameover, 3 credits, 4 paused
    private var state = 0
    private var time = 0f
    private var last = 0L

    // princess
    private var px = 0f
    private var py = 0f
    private var vx = 0f
    private var vy = 0f
    private var jumpPulse = 0f
    private var capeT = 0f
    private var canFlutter = true

    // camera / world
    private var cameraY = 0f
    private var score = 0
    private var best = 0
    private var level = 1
    private var stars = 0
    private var combo = 0
    private var comboTimer = 0f
    private var spawnY = 0f
    private val rand = Random.Default

    // entities
    private val platforms = mutableListOf<Platform>()
    private val pickups = mutableListOf<Pickup>()
    private val particles = mutableListOf<Particle>()
    private val rings = mutableListOf<Ring>()
    private val floats = mutableListOf<FloatText>()
    private val trail = mutableListOf<TrailDot>()

    // background layers
    private val bgStars = mutableListOf<FloatArray>()
    private val bgClouds = mutableListOf<FloatArray>()

    // fx
    private var flash = 0f
    private var shake = 0f
    private var panelT = 0f
    private var newBest = false
    private var deathHandled = false

    // touch controls: direct responsive finger tracking & tap flutter
    private var touchActive = false
    private var targetTouchX = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasDragged = false

    // buttons
    private val btnPlay = GameButton("JUGAR  ✦", 280f * dp, 58f * dp, C.PINK, Color.rgb(255, 60, 130))
    private val btnCredits = GameButton("CRÉDITOS", 280f * dp, 58f * dp, C.PURPLE, Color.rgb(80, 66, 158))
    private val btnSound = GameButton("SONIDO: ON", 280f * dp, 58f * dp, Color.rgb(38, 160, 152), Color.rgb(24, 120, 114))
    private val btnBack = GameButton("VOLVER", 260f * dp, 56f * dp, C.PURPLE, Color.rgb(80, 66, 158))
    private val btnRetry = GameButton("REINTENTAR", 280f * dp, 58f * dp, C.PINK, Color.rgb(255, 60, 130))
    private val btnMenu = GameButton("MENÚ", 280f * dp, 58f * dp, C.PURPLE, Color.rgb(80, 66, 158))
    private val btnResume = GameButton("CONTINUAR", 280f * dp, 58f * dp, Color.rgb(38, 160, 152), Color.rgb(24, 120, 114))
    private val btnExit = GameButton("SALIR AL MENÚ", 280f * dp, 58f * dp, C.PURPLE, Color.rgb(80, 66, 158))
    private val btnPause = GameButton("❚❚", 56f * dp, 48f * dp, Color.argb(140, 27, 20, 78), Color.argb(140, 27, 20, 78))

    private val prefs = context.getSharedPreferences("sky", 0)

    init {
        best = prefs.getInt("best", 0)
        btnSound.label = if (snd.soundOn) "SONIDO: ON" else "SONIDO: OFF"
        buildGlow()
        buildSparkle()
    }

    // -------------------------------------------------- setup helpers

    private fun buildGlow() {
        val g = RadialGradient(
            32f, 32f, 32f,
            intArrayOf(Color.WHITE, 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        val gp = Paint().apply { shader = g }
        Canvas(glowBmp).drawCircle(32f, 32f, 32f, gp)
    }

    private fun glowPaint(color: Int): Paint =
        glowPaints.getOrPut(color) {
            Paint().apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        }

    private fun drawGlow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        val gp = glowPaint(color)
        gp.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(glowBmp, null, RectF(x - r, y - r, x + r, y + r), gp)
    }

    private fun buildSparkle() {
        sparklePath.moveTo(0f, -1f)
        sparklePath.quadTo(0f, 0f, 1f, 0f)
        sparklePath.quadTo(0f, 0f, 0f, 1f)
        sparklePath.quadTo(0f, 0f, -1f, 0f)
        sparklePath.quadTo(0f, 0f, 0f, -1f)
        sparklePath.close()
    }

    private fun drawSparkle(c: Canvas, x: Float, y: Float, r: Float, color: Int, rot: Float = 0f) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        c.scale(r, r)
        p.color = color
        p.style = Paint.Style.FILL
        c.drawPath(sparklePath, p)
        c.restore()
    }

    // -------------------------------------------------- lifecycle

    fun onActivityPause() {
        snd.pauseMusic()
    }

    fun onActivityResume() {
        snd.startMusic()
    }

    fun onDestroy() {
        snd.release()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutButtons()
        buildBackground()
    }

    private fun layoutButtons() {
        val w = width.toFloat()
        val h = height.toFloat()

        val btnW = min(w * 0.82f, 320f * dp)
        val btnH = 62f * dp
        listOf(btnPlay, btnSound, btnCredits, btnBack, btnRetry, btnMenu, btnResume, btnExit).forEach {
            it.w = btnW
            it.h = btnH
        }
        btnPause.w = 58f * dp
        btnPause.h = 50f * dp

        btnPlay.layout(w / 2f, h * 0.605f)
        btnSound.layout(w / 2f, h * 0.705f)
        btnCredits.layout(w / 2f, h * 0.805f)
        btnBack.layout(w / 2f, h * 0.805f)
        btnRetry.layout(w / 2f, h * 0.63f)
        btnMenu.layout(w / 2f, h * 0.73f)
        btnResume.layout(w / 2f, h * 0.54f)
        btnExit.layout(w / 2f, h * 0.65f)
        btnPause.layout(w - 20f * dp - btnPause.w / 2f, 20f * dp + btnPause.h / 2f)
    }

    private fun buildBackground() {
        bgStars.clear()
        for (i in 0 until 28) {
            bgStars.add(
                floatArrayOf(
                    rand.nextFloat() * width,
                    rand.nextFloat() * height * 0.75f,
                    (1.5f + rand.nextFloat() * 2.5f) * dp,
                    0.8f + rand.nextFloat() * 2.4f,
                    rand.nextFloat() * 6.28f
                )
            )
        }
        bgClouds.clear()
        for (i in 0 until 7) {
            val layer = if (i < 4) 0 else 1
            bgClouds.add(
                floatArrayOf(
                    rand.nextFloat() * width,
                    rand.nextFloat() * height,
                    (0.6f + rand.nextFloat() * 0.8f) * dp,
                    (4f + rand.nextFloat() * 7f) * (if (layer == 0) 0.4f else 1f) * dp,
                    if (layer == 0) 40f else 85f,
                    layer.toFloat()
                )
            )
        }
    }

    // -------------------------------------------------- game flow

    private fun reset() {
        state = 1
        score = 0
        level = 1
        stars = 0
        combo = 0
        comboTimer = 0f
        cameraY = 0f
        vx = 0f
        vy = 0f
        newBest = false
        deathHandled = false
        panelT = 0f
        canFlutter = true
        touchActive = false
        hasDragged = false

        px = width / 2f
        py = height * 0.68f

        platforms.clear()
        pickups.clear()
        particles.clear()
        rings.clear()
        floats.clear()
        trail.clear()

        val startPlW = 160f * dp
        platforms.add(
            Platform(
                width / 2f - startPlW / 2f, height * 0.78f, startPlW, 26f * dp,
                T_NORMAL, 0, width / 2f - startPlW / 2f, 0f, 0f, 0f
            )
        )
        spawnY = height * 0.78f
        repeat(14) { addPlatform() }
        flash = 0.4f
        snd.play(SoundManager.S_CLICK)
    }

    private fun addPlatform() {
        val lvl = level
        val gap = (75f + rand.nextInt(32) + min(36f, lvl * 2.2f)) * dp
        spawnY -= gap
        val w = (105f - min(28f, (lvl - 1) * 3f) + rand.nextInt(30)) * dp
        val margin = 20f * dp
        val maxX = (width - w - margin).toInt().coerceAtLeast(1)
        val x = margin + rand.nextInt(maxX)

        var type = T_NORMAL
        val roll = rand.nextFloat()
        val movingChance = min(0.35f, 0.08f + lvl * 0.04f)
        if (roll < 0.10f) type = T_BOOST
        else if (roll < 0.10f + movingChance && platforms.size > 2) type = T_MOVING

        val hue = rand.nextInt(3)
        val speed = if (type == T_MOVING) (36f + rand.nextFloat() * 45f + lvl * 3f) * dp else 0f
        var range = if (type == T_MOVING) (35f + rand.nextFloat() * 75f) * dp else 0f
        if (type == T_MOVING) {
            range = min(range, min(x - margin, width - margin - (x + w)))
            range = range.coerceAtLeast(10f * dp)
        }
        platforms.add(Platform(x, spawnY, w, 24f * dp, type, hue, x, rand.nextFloat() * 6.28f, speed, range))

        // pickups above platform
        if (type != T_BOOST && platforms.size > 2) {
            val r = rand.nextFloat()
            if (r < 0.08f) pickups.add(Pickup(x + w / 2f, spawnY - 44f * dp, true, rand.nextFloat() * 6.28f))
            else if (r < 0.45f) pickups.add(Pickup(x + w / 2f, spawnY - 38f * dp, false, rand.nextFloat() * 6.28f))
        }
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int = 12, power: Float = 1f) {
        repeat(n) {
            val a = rand.nextFloat() * 6.28f
            val sp = (60f + rand.nextFloat() * 150f) * power * dp
            particles.add(
                Particle(
                    x, y, cos(a) * sp, sin(a) * sp - 40f * dp,
                    0.6f + rand.nextFloat() * 0.5f, 1.1f, color,
                    (3f + rand.nextFloat() * 4f) * dp, false, 0f, 0f
                )
            )
        }
    }

    private fun confetti(x: Float, y: Float, n: Int) {
        val colors = intArrayOf(C.PINK, C.GOLD, C.PURPLE_LIGHT, Color.rgb(126, 231, 245), Color.WHITE)
        repeat(n) {
            particles.add(
                Particle(
                    x + (rand.nextFloat() - 0.5f) * 120f * dp, y,
                    (rand.nextFloat() - 0.5f) * 240f * dp, (-rand.nextFloat() * 300f - 60f) * dp,
                    1.4f + rand.nextFloat() * 0.9f, 2.3f,
                    colors[rand.nextInt(colors.size)],
                    (4f + rand.nextFloat() * 5f) * dp, true,
                    (rand.nextFloat() - 0.5f) * 14f, rand.nextFloat() * 6.28f
                )
            )
        }
    }

    private fun levelUp() {
        level++
        stars++
        snd.play(SoundManager.S_POWER)
        confetti(px, py - 60f * dp, 26)
        floats.add(FloatText(px, py - 110f * dp, "¡NIVEL $level!", C.GOLD, 1.4f))
        rings.add(Ring(px, py, 10f * dp, 150f * dp, 0.7f, C.GOLD))
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun die() {
        state = 2
        panelT = 0f
        shake = 1f
        flash = 1f
        touchActive = false
        snd.duckMusic(true)
        snd.play(SoundManager.S_OVER)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        burst(px, py, Color.rgb(255, 160, 200), 20, 1.4f)
        rings.add(Ring(px, py, 10f * dp, 180f * dp, 0.8f, C.PINK))
        if (score > best) {
            best = score
            newBest = true
            prefs.edit().putInt("best", best).apply()
        }
    }

    // -------------------------------------------------- main loop

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.033f)
        last = now
        time += dt

        drawSky(c)

        when (state) {
            0 -> drawMenu(c)
            1 -> {
                update(dt)
                drawWorld(c)
                drawHud(c)
            }
            2 -> {
                updateFx(dt)
                drawWorld(c)
                drawGameOver(c)
            }
            3 -> drawCredits(c)
            4 -> {
                drawWorld(c)
                drawHud(c)
                drawPaused(c)
            }
        }

        // global flash overlay
        if (flash > 0f) {
            flash = (flash - dt * 2.4f).coerceAtLeast(0f)
            p.color = Color.argb((flash * 170).toInt(), 255, 255, 255)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        }

        postInvalidateOnAnimation()
    }

    // -------------------------------------------------- background

    private fun drawSky(c: Canvas) {
        val g = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            C.SKY_TOP, C.SKY_BOT, Shader.TileMode.CLAMP
        )
        p.shader = g
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null

        // moon with halo
        val mx = width * 0.82f
        val my = height * 0.12f
        drawGlow(c, mx, my, 95f * dp, Color.rgb(255, 240, 220), 70)
        p.color = Color.rgb(255, 245, 225)
        c.drawCircle(mx, my, 34f * dp, p)
        p.color = Color.argb(40, 160, 130, 190)
        c.drawCircle(mx - 10f * dp, my - 8f * dp, 7f * dp, p)
        c.drawCircle(mx + 12f * dp, my + 6f * dp, 9f * dp, p)
        c.drawCircle(mx + 2f * dp, my + 16f * dp, 5f * dp, p)

        // twinkling stars
        for (s in bgStars) {
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(time * s[3] + s[4]))) * 200f
            p.color = Color.argb(a.toInt(), 255, 255, 255)
            c.drawCircle(s[0], s[1], s[2], p)
        }

        // drifting parallax clouds
        for (cl in bgClouds) {
            cl[0] += cl[3] * 0.016f
            if (cl[0] > width + 200f * dp) cl[0] = -200f * dp
            drawSoftCloud(
                c, cl[0], cl[1], 150f * cl[2], 52f * cl[2],
                Color.argb(cl[4].toInt(), 255, 235, 250)
            )
        }
    }

    private fun drawSoftCloud(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, p)
        c.drawCircle(x + w * 0.25f, y, h * 0.55f, p)
        c.drawCircle(x + w * 0.52f, y - h * 0.28f, h * 0.75f, p)
        c.drawCircle(x + w * 0.78f, y + h * 0.05f, h * 0.6f, p)
    }

    // -------------------------------------------------- menu

    private fun drawMenu(c: Canvas) {
        // elegant dark glass card container for contrast against sky
        val cardMargin = 16f * dp
        val cardTop = max(height * 0.065f, 44f * dp)
        val cardBot = height * 0.955f
        p.color = Color.argb(195, 20, 14, 54)
        c.drawRoundRect(cardMargin, cardTop, width - cardMargin, cardBot, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(cardMargin, cardTop, width - cardMargin, cardBot, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.FILL

        // floating ambience sparkles
        repeat(3) { i ->
            val t = time * 0.9f + i * 2.1f
            val sx = width * (0.22f + 0.28f * i) + sin(t) * (24f * dp)
            val sy = cardTop + (60f + 35f * i) * dp + cos(t * 1.3f) * (14f * dp)
            drawSparkle(c, sx, sy, (5f + 2f * sin(t * 2f)) * dp, Color.argb(190, 255, 235, 160), t * 40f)
        }

        // title with shimmer (positioned safely below camera cutout)
        val titleY = cardTop + 62f * dp
        drawGlow(c, width / 2f, titleY - 14f * dp, 140f * dp, C.PINK, 60)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 46f * dp
        text.shader = LinearGradient(
            width / 2f - 140f * dp, titleY - 40f * dp, width / 2f + 140f * dp, titleY,
            intArrayOf(Color.WHITE, C.GOLD, Color.WHITE, C.PINK_LIGHT, Color.WHITE),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        c.drawText("SKY DREAMS", width / 2f, titleY, text)
        text.shader = null

        textMed.textAlign = Paint.Align.CENTER
        textMed.textSize = 18f * dp
        textMed.color = Color.rgb(255, 236, 170)
        c.drawText("✦ Grace, la princesa de las nubes ✦", width / 2f, titleY + 34f * dp, textMed)

        // floating princess with glow
        val fy = height * 0.39f + sin(time * 1.6f) * (9f * dp)
        drawGlow(c, width / 2f, fy, 95f * dp, C.GOLD, 40)
        drawPrincess(c, width / 2f, fy, 1.45f * dp)
        drawSparkle(c, width / 2f + 42f * dp, fy - 48f * dp + sin(time * 2f) * (5f * dp), 6f * dp, C.GOLD, time * 60f)

        // best score chip with border
        val chipW = 230f * dp
        val chipH = 40f * dp
        val chipY = height * 0.495f
        p.color = Color.argb(230, 34, 25, 82)
        c.drawRoundRect(
            width / 2f - chipW / 2f, chipY,
            width / 2f + chipW / 2f, chipY + chipH, 20f * dp, 20f * dp, p
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = Color.argb(160, 255, 229, 119)
        c.drawRoundRect(
            width / 2f - chipW / 2f, chipY,
            width / 2f + chipW / 2f, chipY + chipH, 20f * dp, 20f * dp, p
        )
        p.style = Paint.Style.FILL

        textMed.textAlign = Paint.Align.CENTER
        textMed.textSize = 18f * dp
        textMed.color = C.GOLD
        val recordLabel = if (best > 0) "★  RÉCORD  $best m" else "★ ¡ALCANZA EL CIELO! ★"
        c.drawText(recordLabel, width / 2f, chipY + chipH * 0.68f, textMed)

        // buttons with extra bold prominent text sizes
        drawButton(c, btnPlay, 29f * dp)
        drawButton(c, btnSound, 25f * dp)
        drawButton(c, btnCredits, 25f * dp)

        textMed.textAlign = Paint.Align.CENTER
        textMed.textSize = 15.5f * dp
        textMed.color = Color.rgb(245, 235, 255)
        c.drawText("Usa tu dedo para guiar a Grace  •  EBYZOM E.I.R.L.", width / 2f, height * 0.92f, textMed)
    }

    private fun drawButton(c: Canvas, b: GameButton, txtSize: Float) {
        b.press = (b.press - 0.08f).coerceAtLeast(0f)
        val s = 1f - b.press * 0.05f
        val halfW = b.w / 2f * s
        val halfH = b.h / 2f * s

        // soft drop shadow
        p.color = Color.argb(110, 15, 10, 40)
        c.drawRoundRect(
            b.x - halfW, b.y - halfH + 6f * dp, b.x + halfW, b.y + halfH + 6f * dp,
            halfH, halfH, p
        )

        // gradient body
        val g = LinearGradient(
            0f, b.y - halfH, 0f, b.y + halfH,
            b.colorTop, b.colorBot, Shader.TileMode.CLAMP
        )
        p.shader = g
        c.drawRoundRect(b.x - halfW, b.y - halfH, b.x + halfW, b.y + halfH, halfH, halfH, p)
        p.shader = null

        // inner bright border for contrast
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * dp
        p.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(b.x - halfW, b.y - halfH, b.x + halfW, b.y + halfH, halfH, halfH, p)
        p.style = Paint.Style.FILL

        // glossy top highlight
        p.color = Color.argb(55, 255, 255, 255)
        c.drawRoundRect(
            b.x - halfW + 6f * dp, b.y - halfH + 3f * dp,
            b.x + halfW - 6f * dp, b.y - halfH + b.h * 0.38f,
            halfH * 0.8f, halfH * 0.8f, p
        )

        // large, bold label with mathematical vertical centering
        btnText.textSize = txtSize
        val fontMetrics = btnText.fontMetrics
        val textOffset = (fontMetrics.ascent + fontMetrics.descent) / 2f
        c.drawText(b.label, b.x, b.y - textOffset, btnText)
    }

    // -------------------------------------------------- gameplay update

    private fun update(dt: Float) {
        capeT += dt

        // responsive horizontal steering with finger tracking
        if (touchActive) {
            val diff = targetTouchX - px
            val targetVx = (diff * 9f).coerceIn(-maxSpeed, maxSpeed)
            vx += (targetVx - vx) * (18f * dt)
        } else {
            // natural deceleration
            vx *= (1f - 4f * dt).coerceIn(0.80f, 0.98f)
        }

        px += vx * dt

        // wrap-around edges
        val edgeMargin = 20f * dp
        if (px < -edgeMargin) px = width + edgeMargin
        if (px > width + edgeMargin) px = -edgeMargin

        vy += gravity * dt
        py += vy * dt

        // trail
        trail.add(TrailDot(px, py + 14f * dp, 0.32f))
        if (trail.size > 40) trail.removeAt(0)

        val worldFloor = height * 0.84f
        if (py > worldFloor + 90f * dp && !deathHandled) {
            deathHandled = true
            die()
            return
        }

        val footY = py + 26f * dp
        val footHalfW = 16f * dp

        for (pl in platforms) {
            if (pl.type == T_MOVING) {
                pl.phase += pl.speed * dt / maxOf(30f * dp, pl.range)
                pl.x = pl.baseX + sin(pl.phase) * pl.range
            }
            val yScreen = pl.y + cameraY

            // jump collision
            if (vy > 0 && footY > yScreen && footY < yScreen + 26f * dp &&
                px + footHalfW > pl.x && px - footHalfW < pl.x + pl.w
            ) {
                py = yScreen - 26f * dp
                vy = if (pl.type == T_BOOST) -boostSpeed else -jumpSpeed
                score = maxOf(score, ((height * 0.78f - (pl.y + cameraY)) / (10f * dp)).toInt())
                jumpPulse = 1f
                canFlutter = true // refresh air flutter on cloud bounce!

                if (pl.type == T_BOOST) {
                    snd.play(SoundManager.S_POWER)
                    rings.add(Ring(px, yScreen, 8f * dp, 120f * dp, 0.5f, C.GOLD))
                    burst(px, yScreen, C.GOLD, 16, 1.3f)
                    floats.add(FloatText(px, yScreen - 35f * dp, "¡IMPULSO!", C.GOLD, 0.9f))
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } else {
                    snd.play(SoundManager.S_JUMP, 0.85f)
                    burst(px, yScreen, Color.rgb(255, 243, 173), 8)
                    rings.add(Ring(px, yScreen, 6f * dp, 75f * dp, 0.4f, Color.WHITE))
                }
            }
        }

        // pickups
        for (pk in pickups) {
            if (pk.taken) continue
            val dy = pk.y + cameraY
            val dx = pk.x - px
            if (abs(dx) < 32f * dp && abs(dy - py) < 36f * dp) {
                pk.taken = true
                canFlutter = true
                if (pk.gem) {
                    score += 50
                    stars += 5
                    snd.play(SoundManager.S_POWER)
                    confetti(pk.x, dy, 20)
                    floats.add(FloatText(pk.x, dy - 20f * dp, "+50", C.GOLD, 1.1f))
                    rings.add(Ring(pk.x, dy, 8f * dp, 120f * dp, 0.6f, C.GOLD))
                    shake = 0.35f
                } else {
                    combo = min(9, combo + 1)
                    comboTimer = 2.6f
                    val pts = 10 * combo
                    score += pts
                    stars++
                    snd.play(SoundManager.S_STAR)
                    burst(pk.x, dy, C.GOLD, 10)
                    floats.add(
                        FloatText(
                            pk.x, dy - 16f * dp,
                            if (combo > 1) "+$pts  x$combo" else "+$pts", C.GOLD, 0.9f
                        )
                    )
                    rings.add(Ring(pk.x, dy, 6f * dp, 65f * dp, 0.45f, C.GOLD))
                }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // combo decay
        if (combo > 0) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }

        val newLevel = score / 150 + 1
        if (newLevel > level) levelUp()

        // camera follow
        if (py < height * 0.36f) {
            val delta = height * 0.36f - py
            py = height * 0.36f
            cameraY += delta
            spawnY += delta
        }

        while (platforms.size < 18) addPlatform()
        platforms.removeAll { it.y + cameraY > height + 90f * dp }
        pickups.removeAll { it.y + cameraY > height + 90f * dp || it.taken }

        updateFx(dt)

        jumpPulse = maxOf(0f, jumpPulse - dt * 3.2f)
        shake = maxOf(0f, shake - dt * 2.2f)
    }

    private fun updateFx(dt: Float) {
        for (s in particles) {
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.confetti) {
                s.vy += 420f * dp * dt
                s.vx *= 0.99f
                s.rot += s.spin * dt
            } else {
                s.vy += 300f * dp * dt
            }
            s.life -= dt
        }
        particles.removeAll { it.life <= 0 }
        for (r in rings) r.r += (r.maxR - r.r) * dt * 7f
        rings.removeAll { it.r > it.maxR - 1f }
        for (f in floats) {
            f.y -= 46f * dp * dt
            f.life -= dt
        }
        floats.removeAll { it.life <= 0 }
        for (t in trail) t.life -= dt
        trail.removeAll { it.life <= 0 }
        panelT = min(1f, panelT + dt * 2.2f)
    }

    // -------------------------------------------------- world drawing

    private fun drawWorld(c: Canvas) {
        c.save()
        if (shake > 0f) {
            c.translate(
                (rand.nextFloat() - 0.5f) * 14f * dp * shake,
                (rand.nextFloat() - 0.5f) * 14f * dp * shake
            )
        }

        // trail
        for (t in trail) {
            val a = (t.life / 0.32f * 90).toInt()
            p.color = Color.argb(a, 255, 170, 215)
            c.drawCircle(t.x, t.y, 7f * dp * (t.life / 0.32f), p)
        }

        // platforms
        for (pl in platforms) {
            val y = pl.y + cameraY
            if (y < height + 60f * dp && y > -60f * dp) drawPlatform(c, pl, y)
        }

        // pickups
        for (pk in pickups) {
            val y = pk.y + cameraY + sin(time * 3f + pk.phase) * (5f * dp)
            if (y < -40f * dp || y > height + 40f * dp) continue
            if (pk.gem) {
                drawGlow(c, pk.x, y, 34f * dp, C.GOLD, 150)
                drawGem(c, pk.x, y, 14f * dp)
            } else {
                drawGlow(c, pk.x, y, 26f * dp, C.GOLD, 110)
                drawSparkle(c, pk.x, y, (11f + 1.5f * sin(time * 4f + pk.phase)) * dp, C.GOLD, time * 90f + pk.phase)
            }
        }

        // particles
        for (s in particles) {
            val a = (s.life / s.maxLife).coerceIn(0f, 1f)
            if (s.confetti) {
                c.save()
                c.translate(s.x, s.y)
                c.rotate(s.rot)
                p.color = s.color
                p.alpha = (a * 255).toInt()
                c.drawRect(-s.size / 2, -s.size / 3, s.size / 2, s.size / 3, p)
                c.restore()
            } else {
                p.color = s.color
                p.alpha = (a * 255).toInt()
                c.drawCircle(s.x, s.y, s.size * a, p)
                p.alpha = 255
            }
        }

        // rings
        for (r in rings) {
            val a = (1f - r.r / r.maxR).coerceIn(0f, 1f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3.5f * dp
            p.color = Color.argb((a * 220).toInt(), Color.red(r.color), Color.green(r.color), Color.blue(r.color))
            c.drawCircle(r.x, r.y, r.r, p)
            p.style = Paint.Style.FILL
        }

        // princess
        if (state != 2 || py < height) {
            drawGlow(c, px, py, 60f * dp, C.PINK, 50)
            drawPrincess(c, px, py, (1.0f + jumpPulse * 0.09f) * dp)
        }

        // floating texts
        for (f in floats) {
            val a = (f.life / 1.4f).coerceIn(0f, 1f)
            textMed.textAlign = Paint.Align.CENTER
            textMed.textSize = 20f * dp
            textMed.color = Color.argb((a * 255).toInt(), Color.red(f.color), Color.green(f.color), Color.blue(f.color))
            c.drawText(f.text, f.x, f.y, textMed)
        }

        c.restore()
    }

    private fun drawPlatform(c: Canvas, pl: Platform, y: Float) {
        val x = pl.x
        val w = pl.w
        val h = pl.h

        when (pl.type) {
            T_BOOST -> {
                drawGlow(c, x + w / 2, y + h / 2, w * 0.8f, C.GOLD, 90)
                val g = LinearGradient(0f, y, 0f, y + h, C.GOLD, C.GOLD_DEEP, Shader.TileMode.CLAMP)
                p.shader = g
                c.drawRoundRect(x, y, x + w, y + h, 13f * dp, 13f * dp, p)
                p.shader = null
                p.color = Color.rgb(140, 90, 10)
                c.drawCircle(x + w / 2, y + h / 2, h * 0.30f, p)
                p.color = Color.WHITE
                val ay = y + h / 2 + sin(time * 6f) * (2.5f * dp)
                c.drawCircle(x + w / 2, ay, h * 0.18f, p)
            }
            T_MOVING -> {
                val g = LinearGradient(
                    0f, y, 0f, y + h,
                    Color.rgb(126, 231, 245), Color.rgb(66, 170, 205), Shader.TileMode.CLAMP
                )
                p.shader = g
                c.drawRoundRect(x, y, x + w, y + h, 13f * dp, 13f * dp, p)
                p.shader = null
                p.color = Color.argb(70, 255, 255, 255)
                c.drawRoundRect(x + 6f * dp, y + 4f * dp, x + w - 8f * dp, y + 10f * dp, 3f * dp, 3f * dp, p)
            }
            else -> {
                val colors = arrayOf(
                    intArrayOf(Color.rgb(255, 236, 250), Color.rgb(244, 198, 230)),
                    intArrayOf(Color.rgb(218, 244, 255), Color.rgb(170, 205, 245)),
                    intArrayOf(Color.rgb(255, 224, 241), Color.rgb(235, 175, 220))
                )
                val cc = colors[pl.hue]
                val g = LinearGradient(0f, y, 0f, y + h, cc[0], cc[1], Shader.TileMode.CLAMP)
                p.shader = g
                c.drawRoundRect(x, y, x + w, y + h, 13f * dp, 13f * dp, p)
                p.shader = null
                p.color = Color.argb(85, 255, 255, 255)
                c.drawRoundRect(x + 6f * dp, y + 4f * dp, x + w - 8f * dp, y + 10f * dp, 3f * dp, 3f * dp, p)
            }
        }
    }

    private fun drawGem(c: Canvas, x: Float, y: Float, r: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(sin(time * 2f) * 14f)
        p.color = Color.rgb(126, 231, 245)
        c.drawRoundRect(-r * 0.8f, -r, r * 0.8f, r, r * 0.35f, r * 0.35f, p)
        p.color = Color.argb(150, 255, 255, 255)
        c.drawRoundRect(-r * 0.5f, -r * 0.75f, -r * 0.1f, r * 0.2f, r * 0.2f, r * 0.2f, p)
        c.restore()
    }

    private fun drawPrincess(c: Canvas, x: Float, y: Float, scale: Float) {
        c.save()
        c.scale(scale, scale, x, y)

        // cape fluttering
        val flut = sin(capeT * 9f) * 4f
        p.color = Color.rgb(150, 80, 200)
        val cape = Path()
        cape.moveTo(x - 14f, y - 14f)
        cape.quadTo(x - 30f, y + 8f + flut, x - 20f, y + 26f)
        cape.quadTo(x - 6f, y + 14f, x - 2f, y + 20f)
        cape.close()
        c.drawPath(cape, p)

        // dress
        val dress = Path()
        dress.moveTo(x, y - 18f)
        dress.quadTo(x + 24f, y + 6f, x + 19f, y + 27f)
        dress.quadTo(x, y + 33f, x - 19f, y + 27f)
        dress.quadTo(x - 24f, y + 6f, x, y - 18f)
        p.color = C.PINK
        c.drawPath(dress, p)
        p.color = Color.argb(120, 255, 255, 255)
        c.drawCircle(x, y + 8f, 4.5f, p)
        c.drawCircle(x - 8f, y + 16f, 3.5f, p)
        c.drawCircle(x + 8f, y + 16f, 3.5f, p)

        // head
        p.color = Color.rgb(255, 207, 177)
        c.drawCircle(x, y - 28f, 13f, p)

        // hair
        p.color = Color.rgb(101, 56, 128)
        val hair = Path()
        hair.moveTo(x - 13f, y - 28f)
        hair.quadTo(x - 15f, y - 42f, x, y - 42f)
        hair.quadTo(x + 15f, y - 42f, x + 13f, y - 28f)
        hair.quadTo(x + 10f, y - 36f, x, y - 36f)
        hair.quadTo(x - 10f, y - 36f, x - 13f, y - 28f)
        c.drawPath(hair, p)

        // eyes + blush + smile
        p.color = Color.rgb(45, 30, 70)
        c.drawCircle(x - 5f, y - 27f, 1.9f, p)
        c.drawCircle(x + 5f, y - 27f, 1.9f, p)
        p.color = Color.argb(110, 255, 130, 150)
        c.drawCircle(x - 9f, y - 23f, 2.6f, p)
        c.drawCircle(x + 9f, y - 23f, 2.6f, p)
        p.color = Color.rgb(45, 30, 70)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        c.drawArc(RectF(x - 4f, y - 25f, x + 4f, y - 19f), 15f, 150f, false, p)
        p.style = Paint.Style.FILL

        // crown
        val crown = Path()
        crown.moveTo(x - 7f, y - 42f)
        crown.lineTo(x - 5f, y - 49f)
        crown.lineTo(x - 2.5f, y - 45f)
        crown.lineTo(x, y - 51f)
        crown.lineTo(x + 2.5f, y - 45f)
        crown.lineTo(x + 5f, y - 49f)
        crown.lineTo(x + 7f, y - 42f)
        crown.close()
        p.color = C.GOLD
        c.drawPath(crown, p)
        drawSparkle(c, x, y - 52f, 3.2f, Color.WHITE, time * 80f)

        // boots
        p.color = Color.rgb(106, 70, 167)
        c.drawRoundRect(x - 15f, y + 27f, x - 4f, y + 34f, 4f, 4f, p)
        c.drawRoundRect(x + 4f, y + 27f, x + 15f, y + 34f, 4f, 4f, p)

        c.restore()
    }

    // -------------------------------------------------- HUD

    private fun drawHud(c: Canvas) {
        // top panel
        val panelH = 76f * dp
        p.color = C.PANEL
        c.drawRoundRect(14f * dp, 12f * dp, width - 14f * dp, 12f * dp + panelH, 22f * dp, 22f * dp, p)

        text.textAlign = Paint.Align.LEFT
        text.textSize = 12f * dp
        text.color = Color.argb(220, 255, 255, 255)
        c.drawText("ALTURA", 28f * dp, 34f * dp, text)
        text.textSize = 26f * dp
        text.color = C.GOLD
        c.drawText("$score m", 28f * dp, 62f * dp, text)

        // level + progress bar
        textMed.textAlign = Paint.Align.LEFT
        textMed.textSize = 15f * dp
        textMed.color = Color.WHITE
        c.drawText("NIVEL $level", width * 0.38f, 34f * dp, textMed)

        val barX = width * 0.38f
        val barY = 44f * dp
        val barW = width * 0.23f
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(barX, barY, barX + barW, barY + 9f * dp, 4.5f * dp, 4.5f * dp, p)
        val prog = (score % 150) / 150f
        val pg = LinearGradient(barX, 0f, barX + barW, 0f, C.PINK, C.GOLD, Shader.TileMode.CLAMP)
        p.shader = pg
        c.drawRoundRect(barX, barY, barX + barW * prog, barY + 9f * dp, 4.5f * dp, 4.5f * dp, p)
        p.shader = null

        // stars counter
        drawSparkle(c, width * 0.56f, 40f * dp, 9f * dp, C.GOLD, time * 50f)
        textMed.textAlign = Paint.Align.LEFT
        textMed.textSize = 17f * dp
        textMed.color = Color.WHITE
        c.drawText("$stars", width * 0.56f + 16f * dp, 46f * dp, textMed)

        // best
        textMed.textAlign = Paint.Align.RIGHT
        textMed.textSize = 15f * dp
        textMed.color = C.TEXT_SOFT
        c.drawText("★ $best m", width - 28f * dp, 48f * dp, textMed)

        // combo badge
        if (combo > 1) {
            val pulse = 1f + 0.06f * sin(time * 12f)
            c.save()
            c.scale(pulse, pulse, width * 0.38f + 24f * dp, 106f * dp)
            p.color = Color.argb(210, 255, 104, 166)
            c.drawRoundRect(width * 0.38f - 8f * dp, 90f * dp, width * 0.38f + 85f * dp, 122f * dp, 16f * dp, 16f * dp, p)
            textMed.textAlign = Paint.Align.CENTER
            textMed.textSize = 16f * dp
            textMed.color = Color.WHITE
            c.drawText("COMBO x$combo", width * 0.38f + 38f * dp, 112f * dp, textMed)
            c.restore()

            p.color = Color.argb(180, 255, 229, 119)
            c.drawRoundRect(
                width * 0.38f - 8f * dp, 125f * dp,
                width * 0.38f - 8f * dp + 93f * dp * (comboTimer / 2.6f), 129f * dp, 2f * dp, 2f * dp, p
            )
        }

        // pause button
        drawButton(c, btnPause, 18f * dp)
    }

    // -------------------------------------------------- overlays

    private fun drawGameOver(c: Canvas) {
        if (newBest && rand.nextFloat() < 0.12f) {
            confetti(rand.nextFloat() * width, -20f * dp, 2)
        }

        val t = panelT
        val ease = 1f - (1f - t) * (1f - t) * (1f - t)
        val ph = min(height * 0.50f, 440f * dp)
        val pyTop = height - (height - height * 0.25f) * ease

        p.color = Color.argb(225, 22, 16, 62)
        c.drawRoundRect(22f * dp, pyTop, width - 22f * dp, pyTop + ph, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(22f * dp, pyTop, width - 22f * dp, pyTop + ph, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.FILL

        val cy = pyTop
        if (newBest) {
            val pulse = 1f + 0.05f * sin(time * 8f)
            c.save()
            c.scale(pulse, pulse, width / 2f, cy + 50f * dp)
            p.color = C.GOLD
            c.drawRoundRect(width / 2f - 140f * dp, cy + 28f * dp, width / 2f + 140f * dp, cy + 74f * dp, 22f * dp, 22f * dp, p)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 21f * dp
            text.color = Color.rgb(90, 60, 10)
            c.drawText("★ ¡NUEVO RÉCORD! ★", width / 2f, cy + 57f * dp, text)
            c.restore()
        } else {
            text.textAlign = Paint.Align.CENTER
            text.textSize = 34f * dp
            text.color = Color.WHITE
            c.drawText("¡A volar otra vez!", width / 2f, cy + 56f * dp, text)
        }

        textMed.textAlign = Paint.Align.CENTER
        textMed.textSize = 20f * dp
        textMed.color = Color.rgb(255, 230, 245)
        c.drawText("Grace llegó a $score metros", width / 2f, cy + 104f * dp, textMed)

        drawSparkle(c, width / 2f - 130f * dp, cy + 142f * dp, 9f * dp, C.GOLD, time * 60f)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 26f * dp
        text.color = C.GOLD
        c.drawText("Récord: $best m", width / 2f, cy + 150f * dp, text)

        drawButton(c, btnRetry, 26f * dp)
        drawButton(c, btnMenu, 24f * dp)

        textMed.textSize = 16f * dp
        textMed.color = Color.rgb(255, 235, 180)
        c.drawText("✦ $stars estrellas recogidas", width / 2f, cy + 204f * dp, textMed)
    }

    private fun drawPaused(c: Canvas) {
        p.color = Color.argb(175, 15, 10, 45)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)

        val ph = min(height * 0.36f, 330f * dp)
        val top = height * 0.28f
        p.color = Color.argb(225, 28, 20, 78)
        c.drawRoundRect(28f * dp, top, width - 28f * dp, top + ph, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(28f * dp, top, width - 28f * dp, top + ph, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.FILL

        text.textAlign = Paint.Align.CENTER
        text.textSize = 36f * dp
        text.color = Color.WHITE
        c.drawText("PAUSA", width / 2f, top + 56f * dp, text)

        drawButton(c, btnResume, 26f * dp)
        drawButton(c, btnExit, 24f * dp)
    }

    private fun drawCredits(c: Canvas) {
        p.color = Color.argb(190, 22, 16, 60)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)

        val cardMargin = 20f * dp
        val cardTop = height * 0.12f
        val cardBot = height * 0.90f
        p.color = Color.argb(180, 32, 24, 86)
        c.drawRoundRect(cardMargin, cardTop, width - cardMargin, cardBot, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(cardMargin, cardTop, width - cardMargin, cardBot, 28f * dp, 28f * dp, p)
        p.style = Paint.Style.FILL

        text.textAlign = Paint.Align.CENTER
        text.textSize = 36f * dp
        text.color = Color.WHITE
        c.drawText("CRÉDITOS", width / 2f, height * 0.20f, text)

        val fy = height * 0.35f + sin(time * 1.5f) * (8f * dp)
        drawPrincess(c, width / 2f, fy, 1.35f * dp)

        textMed.textAlign = Paint.Align.CENTER
        textMed.textSize = 22f * dp
        textMed.color = C.GOLD
        c.drawText("SKY DREAMS", width / 2f, height * 0.50f, textMed)

        textMed.textSize = 17f * dp
        textMed.color = Color.WHITE
        c.drawText("Creado por EBYZOM E.I.R.L.", width / 2f, height * 0.565f, textMed)
        c.drawText("Con Grace, la princesa de las nubes ✦", width / 2f, height * 0.615f, textMed)
        textMed.color = Color.rgb(220, 205, 255)
        textMed.textSize = 15f * dp
        c.drawText("© 2026 EBYZOM E.I.R.L.", width / 2f, height * 0.670f, textMed)

        drawButton(c, btnBack, 25f * dp)
    }

    // -------------------------------------------------- touch input

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                snd.startMusic()
                if (state == 1) {
                    if (btnPause.hit(x, y, 20f * dp)) {
                        btnPause.press = 1f
                        snd.play(SoundManager.S_CLICK)
                        state = 4
                        snd.pauseMusic()
                        return true
                    }
                    touchActive = true
                    targetTouchX = x
                    touchStartX = x
                    touchStartY = y
                    hasDragged = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == 1) {
                    touchActive = true
                    targetTouchX = x
                    if (abs(x - touchStartX) > 8f * dp || abs(y - touchStartY) > 8f * dp) {
                        hasDragged = true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state == 1) {
                    // Tap with finger triggers magical air flutter / impulso towards obstacles!
                    if (!hasDragged && canFlutter && vy > -jumpSpeed * 0.45f) {
                        canFlutter = false
                        vy = -flutterSpeed
                        snd.play(SoundManager.S_JUMP, 1.25f)
                        burst(px, py + 22f * dp, C.GOLD, 10, 1.1f)
                        rings.add(Ring(px, py + 22f * dp, 6f * dp, 80f * dp, 0.45f, C.GOLD))
                        floats.add(FloatText(px, py - 35f * dp, "¡ALETEO! ✦", C.GOLD, 0.85f))
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    touchActive = false
                } else {
                    handleTap(x, y)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                touchActive = false
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val pad = 18f * dp
        when (state) {
            0 -> {
                if (btnPlay.hit(x, y, pad)) {
                    btnPlay.press = 1f
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    reset()
                } else if (btnSound.hit(x, y, pad)) {
                    btnSound.press = 1f
                    val on = snd.toggleSound()
                    btnSound.label = if (on) "SONIDO: ON" else "SONIDO: OFF"
                    snd.play(SoundManager.S_CLICK)
                } else if (btnCredits.hit(x, y, pad)) {
                    btnCredits.press = 1f
                    snd.play(SoundManager.S_CLICK)
                    state = 3
                }
            }
            2 -> {
                if (panelT > 0.6f) {
                    if (btnRetry.hit(x, y, pad)) {
                        snd.duckMusic(false)
                        reset()
                    } else if (btnMenu.hit(x, y, pad)) {
                        snd.duckMusic(false)
                        snd.play(SoundManager.S_CLICK)
                        state = 0
                    }
                }
            }
            3 -> {
                if (btnBack.hit(x, y, pad)) {
                    btnBack.press = 1f
                    snd.play(SoundManager.S_CLICK)
                    state = 0
                }
            }
            4 -> {
                if (btnResume.hit(x, y, pad)) {
                    snd.play(SoundManager.S_CLICK)
                    state = 1
                    snd.startMusic()
                } else if (btnExit.hit(x, y, pad)) {
                    snd.play(SoundManager.S_CLICK)
                    state = 0
                    snd.startMusic()
                }
            }
        }
    }
}
