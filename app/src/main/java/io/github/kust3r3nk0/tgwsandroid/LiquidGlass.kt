package io.github.kust3r3nk0.tgwsandroid

import android.graphics.RuntimeShader
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** Static, shared procedural backdrop: no capture of controls, secret fields or other apps.
 * Panels sample it in window coordinates with edge refraction and a frosted tint.
 * Transparency composites a partially opaque panel over its real parent backdrop.
 * No animation loop; the normal Material renderer is used when both preferences are off.
 */
@Composable internal fun Modifier.liquidGlass(panel:Boolean=true,rounded:Boolean=true):Modifier {
    val glass=LocalLiquidGlass.current
    val transparent=LocalTransparency.current
    if(!glass && !transparent)return this
    val shader=remember{RuntimeShader(GLASS_SHADER)}
    val brush=remember(shader){ShaderBrush(shader)}
    val view=LocalView.current
    val dark=MaterialTheme.colorScheme.background.luminanceForGlass()<0.5f
    var origin by remember{mutableStateOf(Offset.Zero)}
    val shape=RoundedCornerShape(if(rounded)24.dp else 0.dp)
    val drawing=this.clip(shape).onGloballyPositioned{origin=it.positionInWindow()}.drawBehind{
        shader.setFloatUniform("bounds",size.width,size.height)
        shader.setFloatUniform("origin",origin.x,origin.y)
        shader.setFloatUniform("viewport",view.rootView.width.toFloat().coerceAtLeast(1f),view.rootView.height.toFloat().coerceAtLeast(1f))
        shader.setFloatUniform("radius",if(rounded)24.dp.toPx() else 0f)
        shader.setFloatUniform("dark",if(dark)1f else 0f)
        shader.setFloatUniform("panel",if(panel)1f else 0f)
        shader.setFloatUniform("glass",if(glass)1f else 0f)
        shader.setFloatUniform("transparency",if(transparent)1f else 0f)
        drawRect(brush)
    }
    return if(panel && rounded)drawing.border(1.dp,Brush.linearGradient(listOf(
        Color.White.copy(alpha=if(dark)0.30f else 0.90f),
        MaterialTheme.colorScheme.primary.copy(alpha=0.10f),
        Color.White.copy(alpha=if(dark)0.12f else 0.65f))),shape) else drawing
}

private fun Color.luminanceForGlass()=0.2126f*red+0.7152f*green+0.0722f*blue

// Android 15+ provides RuntimeShader. Uniforms contain geometry/theme only.
private const val GLASS_SHADER="""
uniform float2 bounds;
uniform float2 origin;
uniform float2 viewport;
uniform float radius;
uniform float dark;
uniform float panel;
uniform float glass;
uniform float transparency;
half4 main(float2 p) {
    float2 q=abs(p-bounds*0.5)-(bounds*0.5-radius);
    float distance=length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-radius;
    float rim=exp(-abs(distance)/18.0)*panel*glass;
    float2 normal=(p-bounds*0.5)/max(bounds*0.5,float2(1.0));
    float2 uv=(p+origin+normal*rim*22.0)/viewport;
    float blue=exp(-dot((uv-float2(0.08,0.25))*float2(2.2,1.5),(uv-float2(0.08,0.25))*float2(2.2,1.5)));
    float teal=exp(-dot((uv-float2(0.95,0.78))*float2(2.4,1.8),(uv-float2(0.95,0.78))*float2(2.4,1.8)));
    float3 base=mix(float3(0.91,0.95,0.99),float3(0.035,0.065,0.105),dark);
    base+=blue*mix(float3(-0.12,-0.045,0.0),float3(0.025,0.09,0.16),dark);
    base+=teal*mix(float3(-0.13,0.015,-0.035),float3(0.01,0.12,0.11),dark);
    float3 frost=mix(float3(1.0),float3(0.08,0.12,0.18),dark);
    base=mix(base,frost,panel*0.58);
    float shine=rim*(0.035+0.055*max(-normal.y,0.0));
    // Skia shaders return premultiplied RGBA. Keep the root backdrop opaque;
    // only panels are translucent, never their text or controls.
    float alpha=mix(1.0,mix(0.42,0.60,dark),panel*transparency);
    return half4(half3(clamp(base+shine,0.0,1.0)*alpha),half(alpha));
}
"""
