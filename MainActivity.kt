package com.ebyzom.skydreams

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.setBackgroundDrawable(ColorDrawable(Color.rgb(23,18,65))); setContentView(SkyDreamView(this)) }
}

private data class Platform(var x: Float, var y: Float, var w: Float, var h: Float, var hue: Int = 0)
private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var color: Int)

private class SkyDreamView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val rand = Random(2026)
    private var state = 0 // 0 menu, 1 playing, 2 game over, 3 credits
    private var princessX = 0f; private var princessY = 0f; private var vx = 0f; private var vy = 0f
    private var cameraY = 0f; private var score = 0; private var best = 0; private var level = 1; private var stars = 0
    private var left = false; private var right = false; private var last = 0L; private var spawnY = 0f
    private val platforms = mutableListOf<Platform>(); private val sparks = mutableListOf<Spark>()
    private var jumpPulse = 0f; private var titlePulse = 0f

    init { isFocusable = true; best = context.getSharedPreferences("sky", 0).getInt("best", 0); setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    private fun sfx(freq: Int) { try { tone.startTone(freq, 75) } catch (_: Exception) {} }
    private fun reset() { state=1; score=0; level=1; stars=0; cameraY=0f; vx=0f; vy=0f; princessX=width/2f; princessY=height*0.68f; platforms.clear(); sparks.clear(); platforms.add(Platform(width/2f-72f, height*0.78f, 144f, 28f, 0)); spawnY=height*0.78f; repeat(14){ addPlatform() }; sfx(ToneGenerator.TONE_PROP_BEEP) }
    private fun addPlatform() { spawnY -= (80 + rand.nextInt(42)).toFloat(); val w=90+rand.nextInt(85); val x=24+rand.nextInt(max(1,width-w-48)); platforms.add(Platform(x.toFloat(),spawnY,w.toFloat(),25f,rand.nextInt(3))) }
    private fun burst(x:Float,y:Float,color:Int) { repeat(12){ sparks.add(Spark(x,y,(rand.nextFloat()-.5f)*5f,-rand.nextFloat()*5f-1,1f,color)) } }

    override fun onDraw(c: Canvas) { super.onDraw(c); val dt = if(last==0L) .016f else ((System.nanoTime()-last)/1e9f).coerceIn(.001f,.033f); last=System.nanoTime(); titlePulse += dt
        drawSky(c); when(state){0->drawMenu(c);1->{update(dt);drawWorld(c);drawHud(c)};2->{drawWorld(c);drawHud(c);drawGameOver(c)};3->drawCredits(c)}; invalidate() }

    private fun drawSky(c:Canvas) { val g=LinearGradient(0f,0f,0f,height.toFloat(),Color.rgb(26,21,80),Color.rgb(255,177,214),Shader.TileMode.CLAMP); p.shader=g;c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.shader=null
        p.color=Color.argb(48,255,255,255); repeat(11){val x=((it*97+31)%width).toFloat(); val y=((it*157+40)%height).toFloat();c.drawCircle(x,y,2f+(it%3),p)}
        p.color=Color.argb(80,255,239,255); for(i in 0..4){ val x=(i*190-45).toFloat(); val y=(height*.24f+i%2*100); c.drawOval(x,y,x+190,y+42,p); c.drawOval(x+42,y-20,x+150,y+52,p) }
    }
    private fun drawMenu(c:Canvas) { p.color=Color.argb(120,35,21,90); c.drawRoundRect(22f,height*.12f,width-22f,height*.87f,34f,34f,p); text.textAlign=Paint.Align.CENTER; text.color=Color.WHITE; text.textSize=48f; c.drawText("SKY DREAMS",width/2f,height*.27f,text); text.textSize=21f;text.color=Color.rgb(255,224,246);c.drawText("La princesa de las nubes",width/2f,height*.33f,text)
        drawPrincess(c,width/2f,height*.46f,1.35f); button(c,width/2f,height*.67f, "JUGAR  ✦", Color.rgb(255,104,166)); button(c,width/2f,height*.77f,"CRÉDITOS",Color.rgb(104,91,190)); text.textSize=13f;text.color=Color.WHITE;c.drawText("EBYZOM E.I.R.L.  •  una aventura que sueña alto",width/2f,height*.84f,text)
    }
    private fun button(c:Canvas,x:Float,y:Float,label:String,color:Int){p.color=color;c.drawRoundRect(x-125,y-27,x+125,y+27,28f,28f,p);text.textAlign=Paint.Align.CENTER;text.textSize=18f;text.color=Color.WHITE;c.drawText(label,x,y+7,text)}
    private fun drawCredits(c:Canvas){p.color=Color.argb(160,27,20,78);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);text.textAlign=Paint.Align.CENTER;text.color=Color.WHITE;text.textSize=35f;c.drawText("CRÉDITOS",width/2f,height*.24f,text);text.textSize=20f;text.color=Color.rgb(255,224,246);c.drawText("SKY DREAMS",width/2f,height*.37f,text);text.textSize=16f;text.color=Color.WHITE;c.drawText("Creado por EBYZOM E.I.R.L.",width/2f,height*.46f,text);c.drawText("Diseño, programación y magia",width/2f,height*.51f,text);c.drawText("© 2026 EBYZOM E.I.R.L. Todos los derechos reservados.",width/2f,height*.61f,text);button(c,width/2f,height*.78f,"VOLVER",Color.rgb(104,91,190))}

    private fun update(dt:Float){ val dir=(if(right)1 else 0)-(if(left)1 else 0); vx += dir*38f*dt; vx*=.91f; princessX += vx; if(princessX<25)princessX=width-25f;if(princessX>width-25)princessX=25f; vy += 820f*dt; princessY += vy*dt
        val worldFloor=height*.82f; if(princessY>worldFloor+80){state=2;if(score>best){best=score;context.getSharedPreferences("sky",0).edit().putInt("best",best).apply()};sfx(ToneGenerator.TONE_PROP_NACK)}
        for(pl in platforms){val py=pl.y+cameraY;if(vy>0&&princessY+24>py&&princessY+24<py+25&&princessX>pl.x-16&&princessX<pl.x+pl.w+16){princessY=py-25;vy=-610f;score=max(score,((height*.78f-(pl.y+cameraY))/10).toInt());val newLevel=score/100+1;if(newLevel>level){level=newLevel;stars++;sfx(ToneGenerator.TONE_PROP_ACK)}else sfx(ToneGenerator.TONE_PROP_BEEP);jumpPulse=1f;burst(princessX,py,Color.rgb(255,243,173))}}
        if(princessY<height*.35f){val delta=height*.35f-princessY;princessY=height*.35f;cameraY+=delta;spawnY+=delta}; while(platforms.size<18)addPlatform();platforms.removeAll{it.y+cameraY>height+80}; for(s in sparks){s.x+=s.vx;s.y+=s.vy;s.vy+=8*dt;s.life-=dt};sparks.removeAll{it.life<=0};jumpPulse=max(0f,jumpPulse-dt*3);titlePulse+=dt
    }
    private fun drawWorld(c:Canvas){for(pl in platforms){val y=pl.y+cameraY;if(y<height+40&&y>-50)drawCloud(c,pl.x,y,pl.w,pl.h,pl.hue)};for(s in sparks){p.color=Color.argb((s.life*255).toInt().coerceIn(0,255),Color.red(s.color),Color.green(s.color),Color.blue(s.color));c.drawCircle(s.x,s.y,4f*s.life,p)};drawPrincess(c,princessX,princessY,1f+jumpPulse*.08f)}
    private fun drawCloud(c:Canvas,x:Float,y:Float,w:Float,h:Float,hue:Int){val colors=arrayOf(Color.rgb(255,236,250),Color.rgb(218,244,255),Color.rgb(255,224,241));p.color=colors[hue];p.setShadowLayer(9f,0f,7f,Color.argb(70,35,28,105));c.drawRoundRect(x,y,x+w,y+h,18f,18f,p);c.drawCircle(x+w*.25f,y,18f,p);c.drawCircle(x+w*.53f,y-10,27f,p);c.drawCircle(x+w*.76f,y+1,20f,p);p.clearShadowLayer();p.color=Color.argb(80,255,255,255);c.drawRoundRect(x+14,y+5,x+w-16,y+11,5f,5f,p)}
    private fun drawPrincess(c:Canvas,x:Float,y:Float,scale:Float){c.save();c.scale(scale,scale,x,y);p.color=Color.rgb(255,207,177);c.drawCircle(x,y-28,13f,p);p.color=Color.rgb(101,56,128);c.drawCircle(x-9,y-34,7f,p);c.drawCircle(x+8,y-35,7f,p);p.color=Color.rgb(255,104,166);c.drawOval(x-22,y-17,x+22,y+26,p);p.color=Color.rgb(255,238,255);c.drawCircle(x-5,y-29,2f,p);c.drawCircle(x+5,y-29,2f,p);p.color=Color.rgb(106,70,167);c.drawRoundRect(x-17,y+18,x-4,y+33,5f,5f,p);c.drawRoundRect(x+4,y+18,x+17,y+33,5f,5f,p);p.color=Color.rgb(255,235,117);c.drawCircle(x+13,y-42,5f,p);c.restore()}
    private fun drawHud(c:Canvas){p.color=Color.argb(130,27,20,78);c.drawRoundRect(16f,16f, width-16f,73f,24f,24f,p);text.textAlign=Paint.Align.LEFT;text.textSize=16f;text.color=Color.WHITE;c.drawText("ALTURA",34f,39f,text);text.textSize=25f;text.color=Color.rgb(255,235,117);c.drawText("${score} m",34f,62f,text);text.textSize=15f;text.color=Color.WHITE;c.drawText("NIVEL $level",width*.43f,40f,text);c.drawText("★".repeat(stars.coerceAtMost(5)),width*.43f,62f,text);text.textAlign=Paint.Align.RIGHT;c.drawText("MEJOR  $best m",width-32f,51f,text);p.color=Color.argb(70,255,255,255);c.drawRoundRect(20f,height-78f,145f,height-26f,25f,25f,p);c.drawRoundRect(width-145f,height-78f,width-20f,height-26f,25f,25f,p);text.textAlign=Paint.Align.CENTER;text.textSize=28f;c.drawText("‹",82f,height-43f,text);c.drawText("›",width-82f,height-43f,text)}
    private fun drawGameOver(c:Canvas){p.color=Color.argb(185,26,20,75);c.drawRoundRect(28f,height*.27f,width-28f,height*.73f,30f,30f,p);text.textAlign=Paint.Align.CENTER;text.color=Color.WHITE;text.textSize=35f;c.drawText("¡A volar otra vez!",width/2f,height*.39f,text);text.textSize=19f;c.drawText("Llegaste a $score metros",width/2f,height*.47f,text);text.color=Color.rgb(255,235,117);text.textSize=22f;c.drawText("Récord: $best m",width/2f,height*.54f,text);button(c,width/2f,height*.65f,"REINTENTAR",Color.rgb(255,104,166))}

    override fun onTouchEvent(e:android.view.MotionEvent):Boolean {val x=e.x;val y=e.y;when(e.action){MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE->{if(state==1){left=x<width/2;right=x>=width/2}};MotionEvent.ACTION_UP->{left=false;right=false;if(state==0&&y>height*.6&&y<height*.72)reset() else if(state==0&&y>height*.72)state=3 else if(state==2&&y>height*.58&&y<height*.72)reset() else if(state==3)state=0};};return true}
}
