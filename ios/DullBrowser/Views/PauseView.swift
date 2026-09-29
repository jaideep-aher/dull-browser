import SwiftUI

/// A quiet stop in front of a site on the pause list. Going back is the easy choice;
/// continuing waits for the countdown.
struct PauseView: View {
    let request: PauseRequest
    let goBack: () -> Void
    let proceed: () -> Void

    var body: some View {
        TimelineView(.periodic(from: request.shownAt, by: 1)) { context in
            let remaining = request.remaining(at: context.date)
            let seconds = Int(remaining.rounded(.up))
            VStack(spacing: 0) {
                Spacer()
                ZStack {
                    Circle().stroke(Theme.hairline, lineWidth: 2)
                    Circle()
                        .trim(from: 0, to: request.delay > 0 ? remaining / request.delay : 0)
                        .stroke(Theme.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 1), value: seconds)
                    Text(seconds > 0 ? "\(seconds)" : "")
                        .font(.system(size: 22, weight: .light))
                        .monospacedDigit()
                        .foregroundStyle(Theme.muted)
                }
                .frame(width: 88, height: 88)
                .padding(.bottom, 36)
                .accessibilityHidden(true)

                Text(SiteName.display(request.site))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .accessibilityIdentifier("pauseSite")
                Text("Do you really want this?")
                    .font(.system(size: 26, weight: .light))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.ink)
                    .padding(.top, 10)
                    .accessibilityIdentifier("pauseQuestion")
                Text("Take a breath. It will still be there later.")
                    .font(.system(size: 15))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.muted)
                    .padding(.top, 12)
                Spacer()

                Button(action: goBack) {
                    Text("Go back")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(Theme.paper)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.ink))
                }
                .accessibilityIdentifier("pauseGoBack")

                Button(action: proceed) {
                    Text(seconds > 0 ? "Continue in \(seconds)s" : "Continue")
                        .font(.system(size: 15))
                        .monospacedDigit()
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                }
                .disabled(seconds > 0)
                .padding(.top, 8)
                .accessibilityIdentifier("pauseContinue")
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.paper.ignoresSafeArea())
        }
    }
}
