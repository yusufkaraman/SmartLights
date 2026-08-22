import QtQuick

// Basit HSV renk çarkı: açı = hue (0-360), merkeze uzaklık = saturation.
// Value sabit 1 tutuluyor; parlaklık zaten ayrı bir slider ile kontrol
// ediliyor (plan madde 11).
//
// NOT: Çark, piksel-piksel createImageData()/putImageData() ile değil,
// açısal dilimler (arc+fill, tam doygun hue) + üstüne beyazdan şeffafa
// radyal gradyan overlay ile çiziliyor. Bu ortamda putImageData() sessizce
// hiçbir şey çizmiyordu (çizim hatasız tamamlanıyor ama ekrana yansımıyordu
// — Qt Quick Canvas'ın bazı render backend'lerinde bilinen bir kısıt);
// arc/fillStyle/createRadialGradient standart ve doğrulanmış şekilde
// çalışıyor, görsel sonuç gerçek bir HSV çarkına çok yakın bir yaklaşım.
Item {
    id: root
    objectName: "colorWheel"

    signal colorPicked(int r, int g, int b)

    function redraw() {
        canvas.requestPaint()
    }

    function hsvToRgb(h, s, v) {
        var c = v * s
        var x = c * (1 - Math.abs((h / 60) % 2 - 1))
        var m = v - c
        var r, g, b
        if (h < 60) { r = c; g = x; b = 0 }
        else if (h < 120) { r = x; g = c; b = 0 }
        else if (h < 180) { r = 0; g = c; b = x }
        else if (h < 240) { r = 0; g = x; b = c }
        else if (h < 300) { r = x; g = 0; b = c }
        else { r = c; g = 0; b = x }
        return [Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)]
    }

    Canvas {
        id: canvas
        anchors.fill: parent

        onPaint: {
            try {
                var w = Math.floor(width)
                var h = Math.floor(height)
                if (w <= 0 || h <= 0)
                    return

                var ctx = getContext("2d")
                var cx = w / 2
                var cy = h / 2
                var radius = Math.min(w, h) / 2

                ctx.clearRect(0, 0, w, h)

                // 1) Hue çemberi: 2 derecelik açısal dilimler, her biri tam
                // doygun (sat=1) tek renk.
                var step = (2 * Math.PI) / 180
                for (var a = 0; a < 2 * Math.PI; a += step) {
                    var hue = (a * 180 / Math.PI) % 360
                    var rgb = root.hsvToRgb(hue, 1, 1)
                    ctx.beginPath()
                    ctx.moveTo(cx, cy)
                    // Dilimler arası boşluk kalmasın diye hafif üst üste bindiriliyor.
                    ctx.arc(cx, cy, radius, a, a + step + 0.02)
                    ctx.closePath()
                    ctx.fillStyle = "rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")"
                    ctx.fill()
                }

                // 2) Merkeze doğru beyaza soluklaşan radyal gradyan: saturation izlenimi.
                var grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius)
                grad.addColorStop(0, "rgba(255,255,255,1)")
                grad.addColorStop(1, "rgba(255,255,255,0)")
                ctx.fillStyle = grad
                ctx.beginPath()
                ctx.arc(cx, cy, radius, 0, 2 * Math.PI)
                ctx.fill()
            } catch (e) {
                console.log("ColorWheel paint hatası: " + e)
            }
        }
    }

    Rectangle {
        id: indicator
        width: 16
        height: 16
        radius: 8
        color: "transparent"
        border.color: "white"
        border.width: 2
        visible: false
    }

    MouseArea {
        id: mouseArea
        anchors.fill: parent
        onPressed: (mouse) => pick(mouse.x, mouse.y)
        onPositionChanged: (mouse) => { if (pressed) pick(mouse.x, mouse.y) }

        function pick(x, y) {
            var cx = width / 2
            var cy = height / 2
            var radius = Math.min(width, height) / 2
            var dx = x - cx
            var dy = y - cy
            var rawDist = Math.sqrt(dx * dx + dy * dy)
            var dist = Math.min(rawDist, radius)
            var angle = Math.atan2(dy, dx)
            var hue = (angle * 180 / Math.PI + 360) % 360
            var sat = dist / radius

            indicator.x = cx + Math.cos(angle) * dist - indicator.width / 2
            indicator.y = cy + Math.sin(angle) * dist - indicator.height / 2
            indicator.visible = true

            var rgb = root.hsvToRgb(hue, sat, 1)
            root.colorPicked(rgb[0], rgb[1], rgb[2])
        }
    }

    onWidthChanged: canvas.requestPaint()
    onHeightChanged: canvas.requestPaint()
    Component.onCompleted: canvas.requestPaint()
}
