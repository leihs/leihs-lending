require "capybara/rspec"
require "selenium-webdriver"

BROWSER_DOWNLOAD_DIR = File.absolute_path(File.expand_path(__FILE__) + "/../../../tmp")

def http_port
  @port ||= Integer(ENV["LEIHS_LENDING_HTTP_PORT"].presence || 3270)
end

def http_host
  @host ||= ENV["LEIHS_LENDING_HTTP_HOST"].presence || "localhost"
end

def http_base_url
  @http_base_url ||= "http://#{http_host}:#{http_port}"
end

def set_capybara_values
  Capybara.app_host = http_base_url
  Capybara.server_port = http_port
end

# Mirrors bin/env/select-tool-versions-manager: TOOL_VERSIONS_MANAGER wins,
# otherwise mise if available, else asdf. The bin/env/*-setup scripts export
# the variable only within their own process, so rspec cannot rely on it
# (on a mise-only executor this shelled out to `asdf where firefox`).
tool_versions_manager = ENV["TOOL_VERSIONS_MANAGER"].to_s.strip
if tool_versions_manager.empty?
  tool_versions_manager = system("type mise > /dev/null 2>&1") ? "mise" : "asdf"
end
firefox_bin_path = if tool_versions_manager == "mise"
  Pathname.new(`mise where firefox`.strip).join("bin/firefox").expand_path.to_s
else
  Pathname.new(`asdf where firefox`.strip).join("bin/firefox").expand_path.to_s
end
# Only pin the binary when it exists: rspec dry runs (feature-tasks-check)
# never start a browser and may run before firefox-setup installed the
# version from .tool-versions; Selenium raises "not a file" otherwise.
Selenium::WebDriver::Firefox.path = firefox_bin_path if File.file?(firefox_bin_path)

if ENV["SPEC_SLOW_MOTION"].present?
  SPEC_SLOW_MOTION_DELAY = ENV["SPEC_SLOW_MOTION"].to_f

  module SlowMotion
    def click(...)
      sleep SPEC_SLOW_MOTION_DELAY
      super
    end

    def set(...)
      sleep SPEC_SLOW_MOTION_DELAY
      super
    end
  end
  Capybara::Node::Element.prepend(SlowMotion)
end

Capybara.register_driver :firefox do |app|
  options = Selenium::WebDriver::Firefox::Options.new(
    binary: firefox_bin_path,
    log_level: :trace
  )
  options.accept_insecure_certs = true
  options.args << "--headless" if ENV["LEIHS_TEST_HEADLESS"].present?

  Capybara::Selenium::Driver.new(app, browser: :firefox, options: options)
end

RSpec.configure do |config|
  set_capybara_values

  Capybara.default_driver = :firefox
  Capybara.current_driver = :firefox

  config.before :all do
    set_capybara_values
    FileUtils.remove_dir(screenshot_dir, force: true)
    FileUtils.mkdir_p(screenshot_dir)
  end

  config.before :each do
    set_capybara_values
  end

  config.after(:each) do |example|
    take_screenshot screenshot_dir unless example.exception.nil?
  end

  config.before(type: :feature) do
    page.driver.browser.manage.window.resize_to(1280, 1200)
  end

  def screenshot_dir
    Pathname(BROWSER_DOWNLOAD_DIR).join("screenshots")
  end

  def take_screenshot(screenshot_dir = nil, name = nil)
    name ||= "#{Time.now.iso8601.tr(":", "-")}.png"
    path = screenshot_dir.join(name)
    case Capybara.current_driver
    when :firefox
      begin
        page.driver.browser.save_screenshot(path)
      rescue
        nil
      end
    else
      Logger.warn "Taking screenshots is not implemented for \
              #{Capybara.current_driver}."
    end
  end
end
