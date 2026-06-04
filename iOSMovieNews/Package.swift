// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MovieNewsIOS",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .executable(name: "MovieNewsIOS", targets: ["MovieNewsIOS"])
    ],
    targets: [
        .executableTarget(
            name: "MovieNewsIOS",
            path: "MovieNewsIOS",
            exclude: ["Tests"],
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "MovieNewsIOSTests",
            dependencies: ["MovieNewsIOS"],
            path: "MovieNewsIOS/Tests"
        )
    ]
)
