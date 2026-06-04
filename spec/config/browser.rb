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

firefox_bin_path = if ENV["TOOL_VERSIONS_MANAGER"] == "mise"
  Pathname.new(`mise where firefox`.strip).join("bin/firefox").expand_path.to_s
else
  Pathname.new(`asdf where firefox`.strip).join("bin/firefox").expand_path.to_s
end
Selenium::WebDriver::Firefox.path = firefox_bin_path

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
