import SwiftUI
import IronlogKit

@main
struct IronlogApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose handles the keyboard itself; the safe area keeps content clear of the notch.
                .ignoresSafeArea(.keyboard)
                .background(Color(red: 0.04, green: 0.04, blue: 0.04).ignoresSafeArea())
                .preferredColorScheme(.dark)
        }
    }
}

/** Hosts the shared Kotlin (Compose Multiplatform) UI. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
