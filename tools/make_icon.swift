// Draws the 1024×1024 app icon: a gold crescent and star over a deep teal sky.
// iOS applies the rounded mask itself, so the image is a full square.
//
// Usage: swift tools/make_icon.swift ios/PrayerTime/Assets.xcassets/AppIcon.appiconset/AppIcon.png

import AppKit
import CoreGraphics

let size = 1024.0
let output = CommandLine.arguments.dropFirst().first ?? "AppIcon.png"

let ctx = CGContext(data: nil, width: Int(size), height: Int(size), bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!

func rgb(_ hex: UInt32) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

// Sky: lighter at the horizon (bottom), as just after Maghrib.
let sky = CGGradient(colorsSpace: nil, colors: [rgb(0x0B3B3A), rgb(0x0E6B62)] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(sky, start: CGPoint(x: 0, y: size), end: CGPoint(x: 0, y: 0), options: [])

// Crescent: a disc with an offset disc cut out of it.
let gold = rgb(0xF2C46D)
let center = CGPoint(x: size * 0.45, y: size * 0.52)
let radius = size * 0.30
let cut = radius * 0.86
let cutCenter = CGPoint(x: center.x + radius * 0.38, y: center.y + radius * 0.16)
let canvas = CGRect(x: 0, y: 0, width: size, height: size)
ctx.saveGState()
ctx.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
ctx.clip()
ctx.addRect(canvas)
ctx.addEllipse(in: CGRect(x: cutCenter.x - cut, y: cutCenter.y - cut, width: cut * 2, height: cut * 2))
ctx.clip(using: .evenOdd)
ctx.setFillColor(gold)
ctx.fill(canvas)
ctx.restoreGState()

// Four-pointed star inside the crescent's opening.
let star = CGPoint(x: size * 0.70, y: size * 0.60)
let outer = size * 0.085, inner = size * 0.024
let path = CGMutablePath()
for i in 0..<8 {
    let angle = Double(i) * .pi / 4 + .pi / 2
    let r = i.isMultiple(of: 2) ? outer : inner
    let p = CGPoint(x: star.x + r * cos(angle), y: star.y + r * sin(angle))
    i == 0 ? path.move(to: p) : path.addLine(to: p)
}
path.closeSubpath()
ctx.addPath(path)
ctx.setFillColor(gold)
ctx.fillPath()

let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: output))
print("Wrote \(output)")
