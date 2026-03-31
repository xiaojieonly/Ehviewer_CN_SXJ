#!/usr/bin/env python3
"""
测试翻译功能
"""

import sys
import os

# 添加脚本目录到路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from translate_strings_online import OnlineTranslator


def test_google_translation():
    """测试Google翻译"""
    print("测试Google翻译...")
    translator = OnlineTranslator('google')
    
    # 测试英文到中文
    result = translator.translate_text('Hello World', 'en', 'zh')
    print(f"英文到中文: 'Hello World' -> '{result}'")
    
    # 测试中文到英文
    result = translator.translate_text('你好世界', 'zh', 'en')
    print(f"中文到英文: '你好世界' -> '{result}'")
    
    print("Google翻译测试完成\n")


def test_baidu_translation():
    """测试百度翻译（需要API密钥）"""
    print("测试百度翻译...")
    # 注意：这里需要提供有效的API密钥
    # translator = OnlineTranslator('baidu', 'appid|secret_key')
    # result = translator.translate_text('Hello World', 'en', 'zh')
    # print(f"百度翻译: 'Hello World' -> '{result}'")
    print("百度翻译测试跳过（需要API密钥）\n")


def test_deepl_translation():
    """测试DeepL翻译（需要API密钥）"""
    print("测试DeepL翻译...")
    # 注意：这里需要提供有效的API密钥
    # translator = OnlineTranslator('deepl', 'your_deepl_api_key')
    # result = translator.translate_text('Hello World', 'en', 'zh')
    # print(f"DeepL翻译: 'Hello World' -> '{result}'")
    print("DeepL翻译测试跳过（需要API密钥）\n")


if __name__ == '__main__':
    print("翻译功能测试\n")
    
    try:
        test_google_translation()
        test_baidu_translation()
        test_deepl_translation()
        
        print("所有测试完成！")
        
    except Exception as e:
        print(f"测试失败: {e}")
        sys.exit(1)