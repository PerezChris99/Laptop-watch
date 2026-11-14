import Foundation
import Security
import CryptoKit
import React

/**
 Owner-paired, strict leaf-DER SHA256 trust. Supports self-signed LAN
 certificates only when a short-lived in-person QR's exact hash matches.
 No fallback to regular URLSession, HTTP, system PKI or redirected hosts.
 */
@objc(LaptopWatchPinnedTransport)
final class LaptopWatchPinnedTransport: NSObject {
  @objc static func requiresMainQueueSetup() -> Bool { false }

  private class PinDelegate: NSObject, URLSessionDelegate, URLSessionTaskDelegate {
    let host: String
    let expected: Data
    init(host: String, expected: Data) { self.host = host; self.expected = expected }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
      guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
            challenge.protectionSpace.host == host,
            let trust = challenge.protectionSpace.serverTrust,
            let certificate = SecTrustGetCertificateAtIndex(trust, 0) else {
        completionHandler(.cancelAuthenticationChallenge, nil)
        return
      }
      let der = SecCertificateCopyData(certificate) as Data
      let actual = Data(SHA256.hash(data: der))
      guard actual.count == expected.count,
            zip(actual, expected).reduce(0, { $0 | Int($1.0 ^ $1.1) }) == 0 else {
        completionHandler(.cancelAuthenticationChallenge, nil)
        return
      }
      // A self-signed certificate is trusted *only* as this verified leaf
      // anchor. Basic X509 checks validity; QR pin binds it to the owner.
      SecTrustSetPolicies(trust, SecPolicyCreateBasicX509())
      guard SecTrustSetAnchorCertificates(trust, [certificate] as CFArray) == errSecSuccess,
            SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess,
            SecTrustEvaluateWithError(trust, nil) else {
        completionHandler(.cancelAuthenticationChallenge, nil)
        return
      }
      completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
      completionHandler(nil)
    }
  }

  private func privateIP(_ value: String) -> Bool {
    let parts = value.split(separator: ".", omittingEmptySubsequences: false)
    guard parts.count == 4 else { return false }
    let bytes = parts.compactMap { part -> Int? in
      if part.isEmpty || (part.count > 1 && part.first == "0") ||
         part.contains(where: { !$0.isNumber }) { return nil }
      guard let number = Int(part), number >= 0, number <= 255 else { return nil }
      return number
    }
    guard bytes.count == 4 else { return false }
    return bytes[0] == 10 ||
      (bytes[0] == 172 && (16...31).contains(bytes[1])) ||
      (bytes[0] == 192 && bytes[1] == 168)
  }

  /** Native download writes straight to app-private protected disk, never JS. */
  @objc(downloadPinned:resolver:rejecter:)
  func downloadPinned(_ input: NSDictionary,
                      resolver resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    func fail() { reject("EVIDENCE_UNAVAILABLE", "Private signed evidence download failed", nil) }
    guard let host = input["host"] as? String, privateIP(host),
          let port = input["port"] as? Int, (1024...65535).contains(port),
          let pin = input["tlsFingerprint"] as? String,
          pin.count == 64, pin.allSatisfy({ $0.isHexDigit && !$0.isUppercase }),
          let expected = Data(hexString: pin),
          let path = input["path"] as? String, input["method"] as? String == "GET",
          input["bodyBase64"] as? String == "",
          let headers = input["headers"] as? [String: String],
          let url = URL(string: "https://\(host):\(port)\(path)") else {
      fail()
      return
    }
    let pattern = "^/api/evidence/(photo|video)/([a-f0-9]{32})$"
    let regex = try? NSRegularExpression(pattern: pattern)
    let range = NSRange(path.startIndex..<path.endIndex, in: path)
    guard let match = regex?.firstMatch(in: path, range: range),
          match.range == range,
          let kindRange = Range(match.range(at: 1), in: path),
          let idRange = Range(match.range(at: 2), in: path) else {
      fail()
      return
    }
    let kind = String(path[kindRange])
    let evidenceId = String(path[idRange])
    let keys = ["X-LaptopWatch-Device", "X-LaptopWatch-Nonce",
                "X-LaptopWatch-Timestamp", "X-LaptopWatch-Signature"]
    guard keys.allSatisfy({ (headers[$0]?.count ?? 0) > 0 && (headers[$0]?.count ?? 0) <= 256 }),
          headers.keys.allSatisfy({ keys.contains($0) }) else {
      fail()
      return
    }
    var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData,
                             timeoutInterval: 45)
    request.httpMethod = "GET"
    for key in keys { request.setValue(headers[key], forHTTPHeaderField: key) }
    let config = URLSessionConfiguration.ephemeral
    config.httpCookieStorage = nil
    config.urlCache = nil
    config.timeoutIntervalForResource = 45
    let delegate = PinDelegate(host: host, expected: expected)
    let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    session.downloadTask(with: request) { tempURL, response, error in
      defer { session.invalidateAndCancel() }
      guard error == nil, let response = response as? HTTPURLResponse,
            response.statusCode == 200, let tempURL = tempURL,
            (response.value(forHTTPHeaderField: "Content-Type") ?? "").hasPrefix(
              kind == "photo" ? "image/jpeg" : "video/mp4"
            ) else { fail(); return }
      do {
        let files = FileManager.default
        let directory = try files.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                       appropriateFor: nil, create: true)
          .appendingPathComponent("LaptopWatchEvidence", isDirectory: true)
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        var props = URLResourceValues()
        props.isExcludedFromBackup = true
        var protectedDirectory = directory
        try protectedDirectory.setResourceValues(props)
        let suffix = kind == "photo" ? "jpg" : "mp4"
        let destination = directory.appendingPathComponent("\(kind)-\(evidenceId).\(suffix)")
        let attrs = try files.attributesOfItem(atPath: tempURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        let max = kind == "photo" ? Int64(16 * 1024 * 1024) : Int64(128 * 1024 * 1024)
        guard size > 0 && size <= max else { fail(); return }
        if files.fileExists(atPath: destination.path) { try files.removeItem(at: destination) }
        try files.moveItem(at: tempURL, to: destination)
        try files.setAttributes([.protectionKey: FileProtectionType.complete],
                                ofItemAtPath: destination.path)
        resolve(["uri": destination.absoluteString, "id": evidenceId,
                 "kind": kind, "sizeBytes": size])
      } catch { fail() }
    }.resume()
  }

  @objc(sendPinned:resolver:rejecter:)
  func sendPinned(_ input: NSDictionary,
                  resolver resolve: @escaping RCTPromiseResolveBlock,
                  rejecter reject: @escaping RCTPromiseRejectBlock) {
    func denied() { reject("PINNED_REQUEST_FAILED", "Pinned laptop connection unavailable", nil) }
    guard let host = input["host"] as? String, privateIP(host),
          let port = input["port"] as? Int, (1024...65535).contains(port),
          let pin = input["tlsFingerprint"] as? String,
          pin.count == 64, pin.allSatisfy({ $0.isHexDigit && !$0.isUppercase }),
          let expected = Data(hexString: pin),
          let method = input["method"] as? String,
          let path = input["path"] as? String,
          path.count <= 180, !path.contains("//"), !path.contains(".."),
          (method == "GET" || method == "POST"),
          (path == "/pair/request" && method == "POST") ||
             (path.hasPrefix("/api/") &&
              path.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || "/_-".contains($0)) })),
          let b64 = input["bodyBase64"] as? String,
          b64.count <= 90_000,
          let body = Data(base64Encoded: b64), body.count <= 65_536,
          method != "GET" || body.count == 0,
          let url = URL(string: "https://\(host):\(port)\(path)") else {
      denied()
      return
    }
    let timeout = min(max(input["timeoutMs"] as? Int ?? 15_000, 1_000), 30_000)
    let limit = min(max(input["maxResponseBytes"] as? Int ?? 1_048_576, 1), 134_217_728)
    var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData,
                             timeoutInterval: TimeInterval(timeout) / 1000)
    request.httpMethod = method
    if method == "POST" {
      request.httpBody = body
      request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    }
    if let headers = input["headers"] as? [String: String] {
      let allowed: Set<String> = ["Content-Type","X-LaptopWatch-Device","X-LaptopWatch-Timestamp",
                                  "X-LaptopWatch-Nonce","X-LaptopWatch-Signature"]
      for (key, value) in headers {
        guard allowed.contains(key), value.count <= 256,
              value.unicodeScalars.allSatisfy({ $0.value >= 32 && $0.value <= 126 }) else {
          denied()
          return
        }
        request.setValue(value, forHTTPHeaderField: key)
      }
    }
    let config = URLSessionConfiguration.ephemeral
    config.requestCachePolicy = .reloadIgnoringLocalCacheData
    config.httpCookieStorage = nil
    config.urlCache = nil
    config.timeoutIntervalForRequest = TimeInterval(timeout) / 1000
    config.httpMaximumConnectionsPerHost = 1
    let delegate = PinDelegate(host: host, expected: expected)
    let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    session.dataTask(with: request) { data, response, error in
      defer { session.invalidateAndCancel() }
      guard error == nil, let http = response as? HTTPURLResponse,
            let data = data, data.count <= limit else {
        denied()
        return
      }
      resolve([
        "status": http.statusCode,
        "bodyBase64": data.base64EncodedString(),
        "contentType": http.value(forHTTPHeaderField: "Content-Type") ?? ""
      ])
    }.resume()
  }
}

private extension Data {
  init?(hexString: String) {
    guard hexString.count.isMultiple(of: 2) else { return nil }
    var result = Data(capacity: hexString.count / 2)
    var i = hexString.startIndex
    while i < hexString.endIndex {
      let j = hexString.index(i, offsetBy: 2)
      guard let v = UInt8(hexString[i..<j], radix: 16) else { return nil }
      result.append(v)
      i = j
    }
    self = result
  }
}
