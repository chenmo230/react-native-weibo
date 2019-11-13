require "json"
package = JSON.parse(File.read('package.json'))

Pod::Spec.new do |s|
  s.name             = package['name']
  s.version          = package['version']
  s.summary          = package['description']
  s.requires_arc = true
  s.license      = 'MIT'
  s.homepage     = 'n/a'
  s.source       = { :git => "https://github.com/chenmo230/react-native-weibo" }
  s.author       = 'BA'
  s.source_files = 'ios/**/*.{h,m,a}'
  s.vendored_libraries = 'ios/**/*.a'
  s.platform     = :ios, "8.0"
  s.dependency 'React-Core'
end