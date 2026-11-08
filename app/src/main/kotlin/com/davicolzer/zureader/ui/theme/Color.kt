package com.davicolzer.zureader.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta definida em docs/design/mockup.html. Contraste de texto ≥ 4,5:1 e de bordas ≥ 3:1 nos dois temas.

// Escuro (padrão)
val Night = Color(0xFF1E1B3A)      // fundo (céu noturno)
val Surface = Color(0xFF2A2650)    // cards, sheets
val Divider = Color(0xFF38336A)    // divisórias decorativas
val Outline = Color(0xFF6A63A8)    // bordas de controles (3,1:1 no fundo)
val FezRed = Color(0xFFD7263D)     // ação principal
val FezDeep = Color(0xFFA3172B)    // pressionado, sombra do botão
val CubeGold = Color(0xFFF5B700)   // destaques, letra com dúvida
val ScanTeal = Color(0xFF4FC3C9)   // glifo detectado, sucesso
val Paper = Color(0xFFFFF6E5)      // texto principal
val Muted = Color(0xFFA9A3C9)      // texto secundário
val Ink = Color(0xFF14122A)        // fundo mais escuro (miniaturas, câmera)

// Sempre escuros, nos dois temas: atrás do cubo, na câmera e na foto em tela cheia
val Backdrop = Color(0xFF0B0A18)   // o "céu" atrás do cubo, fundo da tela cheia
val Scrim = Color(0xCC0B0A18)      // fundo dos botões e frases sobre a câmera ou a foto

// Claro
val LightBackground = Color(0xFFF7F3EC)  // papel
val LightSurface = Color(0xFFECE6F2)
val LightDivider = Color(0xFFD5CFE3)
val LightOutline = Color(0xFF8E88B0)
val LightFezRed = Color(0xFFC41E34)
val LightFezDeep = Color(0xFF8F1526)
val LightGold = Color(0xFF8A5A00)       // dourado escurecido para ler no claro
val LightTeal = Color(0xFF0E6E75)
val LightMuted = Color(0xFF5B5580)
val LightInk = Color(0xFFDDD6E8)
