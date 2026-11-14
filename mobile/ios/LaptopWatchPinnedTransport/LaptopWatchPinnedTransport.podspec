Pod::Spec.new do |s|
  s.name = 'LaptopWatchPinnedTransport'
  s.version = '0.1.0'
  s.summary = 'Private owner-paired certificate-pinned HTTPS for LaptopWatch'
  s.homepage = 'https://github.com/PerezChris99/Laptop-watch'
  s.source = { :path => '.' }
  s.platform = :ios, '15.1'
  s.source_files = 'Sources/**/*.{swift,m}'
  s.swift_version = '5.0'
  s.dependency 'React-Core'
  s.frameworks = 'Security', 'Foundation'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
